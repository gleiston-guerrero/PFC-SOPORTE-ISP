// El manejador de GET /api/v1/notifications no tenia ninguna prueba: decide el filtro de
// Mongo segun si llega ticketId por query string o no, y ese "si viene, filtra; si no, trae
// todo" es exactamente el tipo de condicion que se invierte por error sin que nadie lo note.
// No hay supertest instalado (ver package.json); se extrae el handler real registrado en el
// router de Express (router.stack[0].route.stack[0].handle) y se invoca directamente con un
// req/res falsos, sin levantar un servidor HTTP -- mismo criterio que las pruebas de filtros
// Servlet del backend Java, que usan MockHttpServletRequest en vez de un servidor real.
const test = require("node:test");
const assert = require("node:assert");

const dbPath = require.resolve("../src/db");

function conColeccionFalsa(findMock, fn) {
  const original = require.cache[dbPath];
  require.cache[dbPath] = {
    id: dbPath,
    filename: dbPath,
    loaded: true,
    exports: { getCollection: async () => ({ find: findMock }) },
  };
  delete require.cache[require.resolve("../src/routes/notifications")];
  try {
    const router = require("../src/routes/notifications");
    const handler = router.stack[0].route.stack[0].handle;
    return fn(handler);
  } finally {
    require.cache[dbPath] = original;
    delete require.cache[require.resolve("../src/routes/notifications")];
  }
}

function resFalsa() {
  const capturas = { json: null };
  return {
    res: { json: (body) => { capturas.json = body; } },
    capturas,
  };
}

function collectionQueFalsa(docs) {
  return (filtro, opciones) => ({
    _filtro: filtro,
    _opciones: opciones,
    sort: () => ({ toArray: async () => docs }),
  });
}

test("sin ticketId en la query, filtra por un objeto vacio (trae todo)", async () => {
  let filtroUsado;
  const find = (filtro, opciones) => {
    filtroUsado = filtro;
    return { sort: () => ({ toArray: async () => [] }) };
  };
  await conColeccionFalsa(find, async (handler) => {
    const { res, capturas } = resFalsa();
    await handler({ query: {} }, res);
    assert.deepStrictEqual(filtroUsado, {});
    assert.strictEqual(capturas.json.message, "OK");
  });
});

test("con ticketId en la query, filtra exactamente por ese ticketId", async () => {
  let filtroUsado;
  const find = (filtro) => {
    filtroUsado = filtro;
    return { sort: () => ({ toArray: async () => [] }) };
  };
  await conColeccionFalsa(find, async (handler) => {
    const { res } = resFalsa();
    await handler({ query: { ticketId: "t1" } }, res);
    assert.deepStrictEqual(filtroUsado, { ticketId: "t1" });
  });
});

test("devuelve los documentos encontrados dentro de la envoltura estandar data/message/timestamp", async () => {
  const documentos = [{ ticketId: "t1", channel: "EMAIL", message: "hola" }];
  await conColeccionFalsa(collectionQueFalsa(documentos), async (handler) => {
    const { res, capturas } = resFalsa();
    await handler({ query: {} }, res);

    assert.deepStrictEqual(capturas.json.data, documentos);
    assert.strictEqual(capturas.json.message, "OK");
    assert.ok(capturas.json.timestamp);
  });
});

test("nunca devuelve el campo _id crudo de Mongo (se excluye en la proyeccion)", async () => {
  let opcionesUsadas;
  const find = (filtro, opciones) => {
    opcionesUsadas = opciones;
    return { sort: () => ({ toArray: async () => [] }) };
  };
  await conColeccionFalsa(find, async (handler) => {
    await handler({ query: {} }, resFalsa().res);
    assert.deepStrictEqual(opcionesUsadas, { projection: { _id: 0 } });
  });
});
