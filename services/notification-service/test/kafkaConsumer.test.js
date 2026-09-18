// handleMessage no tenia ninguna prueba: dispatch() (la decision de canal/mensaje) ya la
// tenia en dispatcher.test.js, pero no la pieza que la conecta con Mongo -- el mapeo de cada
// notificacion decidida a un documento, y que un tipo de evento sin notificaciones (el `default:
// return []` de dispatcher.js) no llegue siquiera a abrir una coleccion.
//
// notification-service no tiene una libreria de mocks instalada (solo node:test / node:assert,
// ver package.json), asi que el doble de src/db.js se inyecta reemplazando la entrada de
// require.cache ANTES de requerir kafkaConsumer.js -- un truco estandar de CommonJS, sin
// dependencias nuevas, que funciona igual en cualquier version de Node (a diferencia de
// node:test mock.module(), que es experimental y no esta disponible en la version 20 que usa
// el flujo de CI).
const test = require("node:test");
const assert = require("node:assert");

const dbPath = require.resolve("../src/db");

function conColeccionFalsa(insertManyMock, fn) {
  const original = require.cache[dbPath];
  require.cache[dbPath] = {
    id: dbPath,
    filename: dbPath,
    loaded: true,
    exports: { getCollection: async () => ({ insertMany: insertManyMock }) },
  };
  delete require.cache[require.resolve("../src/kafkaConsumer")];
  try {
    return fn(require("../src/kafkaConsumer"));
  } finally {
    require.cache[dbPath] = original;
    delete require.cache[require.resolve("../src/kafkaConsumer")];
  }
}

test("ticket.created guarda un documento con el canal y mensaje que decide dispatch", async () => {
  const llamadas = [];
  await conColeccionFalsa(async (docs) => llamadas.push(docs), async ({ handleMessage }) => {
    await handleMessage("ticket.created", { ticketId: "t1", zone: "QUEVEDO_NORTE" });
  });

  assert.strictEqual(llamadas.length, 1);
  assert.strictEqual(llamadas[0].length, 1);
  const doc = llamadas[0][0];
  assert.strictEqual(doc.ticketId, "t1");
  assert.strictEqual(doc.zone, "QUEVEDO_NORTE");
  assert.strictEqual(doc.eventType, "ticket.created");
  assert.strictEqual(doc.channel, "EMAIL");
  assert.strictEqual(doc.simulated, true);
  assert.ok(doc.createdAt);
});

test("ticket.assigned guarda dos documentos, uno por canal", async () => {
  const llamadas = [];
  await conColeccionFalsa(async (docs) => llamadas.push(docs), async ({ handleMessage }) => {
    await handleMessage("ticket.assigned", { ticketId: "t2", zone: "QUEVEDO_SUR" });
  });

  assert.strictEqual(llamadas[0].length, 2);
  const canales = llamadas[0].map((d) => d.channel).sort();
  assert.deepStrictEqual(canales, ["EMAIL", "SMS"]);
  assert.ok(llamadas[0].every((d) => d.eventType === "ticket.assigned"));
});

test("un topico sin notificaciones que despachar no abre la coleccion de Mongo", async () => {
  // dispatch() devuelve [] para cualquier topico no reconocido (rama default) -- handleMessage
  // debe cortar ahi mismo, no llamar a getCollection()/insertMany con una lista vacia.
  let seLlamoInsertMany = false;
  await conColeccionFalsa(async () => { seLlamoInsertMany = true; }, async ({ handleMessage }) => {
    await handleMessage("topico.desconocido", { ticketId: "t3" });
  });

  assert.strictEqual(seLlamoInsertMany, false);
});
