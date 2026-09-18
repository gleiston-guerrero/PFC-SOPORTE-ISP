// logger.js no tenia ninguna prueba: es la implementacion del logging estructurado en JSON
// exigido por la Practica Experimental U5 (item 2a, "logging estructurado (JSON) en todos los
// microservicios") -- sin dependencia nueva (nada de winston/pino), asi que la unica garantia
// de que la linea impresa realmente sea JSON parseable, con los campos correctos, la da esta
// prueba, no una libreria externa ya probada por terceros.
const test = require("node:test");
const assert = require("node:assert");
const logger = require("../src/logger");

function conConsoleCapturada(metodo, fn) {
  const original = console[metodo];
  const llamadas = [];
  console[metodo] = (...args) => llamadas.push(args);
  try {
    fn();
  } finally {
    console[metodo] = original;
  }
  return llamadas;
}

test("info() escribe en console.log (no console.error) una linea JSON parseable", () => {
  const llamadas = conConsoleCapturada("log", () => {
    logger.info("notification-service arriba", { port: 8003 });
  });

  assert.strictEqual(llamadas.length, 1);
  const linea = JSON.parse(llamadas[0][0]);
  assert.strictEqual(linea.level, "info");
  assert.strictEqual(linea.service, "notification-service");
  assert.strictEqual(linea.message, "notification-service arriba");
  assert.strictEqual(linea.port, 8003);
  assert.ok(linea["@timestamp"]);
});

test("error() escribe en console.error, no en console.log", () => {
  // La unica rama real de write(): "level === 'error' ? console.error : console.log" --
  // sin esta prueba, invertir esa condicion pasaria inadvertido.
  const llamadasLog = conConsoleCapturada("log", () => {
    conConsoleCapturada("error", () => {
      logger.error("No se pudo iniciar el consumidor de Kafka", { error: "ECONNREFUSED" });
    });
  });

  assert.strictEqual(llamadasLog.length, 0);
});

test("error() incluye los campos extra (error, stack) en la linea JSON", () => {
  const llamadas = conConsoleCapturada("error", () => {
    logger.error("fallo", { error: "ECONNREFUSED", stack: "Error: ...\n  at x" });
  });

  const linea = JSON.parse(llamadas[0][0]);
  assert.strictEqual(linea.level, "error");
  assert.strictEqual(linea.error, "ECONNREFUSED");
  assert.strictEqual(linea.stack, "Error: ...\n  at x");
});

test("sin campos extra, la linea JSON solo trae los campos base (no un 'undefined' colado)", () => {
  const llamadas = conConsoleCapturada("log", () => {
    logger.info("mensaje simple");
  });

  const linea = JSON.parse(llamadas[0][0]);
  assert.deepStrictEqual(Object.keys(linea).sort(), ["@timestamp", "level", "message", "service"]);
});
