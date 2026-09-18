"""_handle_ticket_created y _consume_loop no tenian ninguna prueba: es la unica
logica real de kafka_consumer.py (el resto son fabricas perezosas de cliente).
Se prueban con dobles de Mongo/Kafka, no contra un broker real -- mismo criterio
que las pruebas de filtros HTTP del backend Java, que usan dobles en vez de
levantar el colaborador de verdad."""

import json
from unittest.mock import MagicMock

from app import kafka_consumer


def test_handle_ticket_created_guarda_la_clasificacion_en_mongo(monkeypatch):
    coleccion = MagicMock()
    productor = MagicMock()
    monkeypatch.setattr(kafka_consumer, "get_collection", lambda: coleccion)
    monkeypatch.setattr(kafka_consumer, "_get_producer", lambda: productor)

    kafka_consumer._handle_ticket_created({
        "ticketId": "0fb2c5be-3e80-46aa-af79-f2aa597f8e4a",
        "zone": "QUEVEDO_NORTE",
        "description": "No tengo internet, corte total en toda la zona, urgente",
    })

    coleccion.update_one.assert_called_once()
    filtro, actualizacion = coleccion.update_one.call_args.args[:2]
    assert filtro == {"ticketId": "0fb2c5be-3e80-46aa-af79-f2aa597f8e4a"}
    campos = actualizacion["$set"]
    assert campos["category"] == "CONECTIVIDAD"
    assert campos["priority"] == "CRITICO"
    assert campos["zone"] == "QUEVEDO_NORTE"
    # upsert=True es lo que hace que el primer mensaje de un ticket cree el
    # documento y no falle por "no encontrado" -- si se perdiera, ai-service
    # dejaria de guardar clasificaciones nuevas en silencio.
    assert coleccion.update_one.call_args.kwargs.get("upsert") is True


def test_handle_ticket_created_publica_ticket_classified_con_el_resultado(monkeypatch):
    coleccion = MagicMock()
    productor = MagicMock()
    monkeypatch.setattr(kafka_consumer, "get_collection", lambda: coleccion)
    monkeypatch.setattr(kafka_consumer, "_get_producer", lambda: productor)

    kafka_consumer._handle_ticket_created({
        "ticketId": "ticket-1",
        "zone": "QUEVEDO_SUR",
        "description": "El router tiene la luz roja parpadeando, hardware dañado",
    })

    productor.send.assert_called_once()
    _, kwargs = productor.send.call_args
    assert kwargs["key"] == "ticket-1"
    assert kwargs["value"]["category"] == "HARDWARE"
    assert kwargs["value"]["ticketId"] == "ticket-1"
    productor.flush.assert_called_once()


def test_handle_ticket_created_sin_descripcion_no_lanza_excepcion(monkeypatch):
    # event.get("description") or "" es la guardia contra un evento sin ese
    # campo -- sin ella, classify(None) rompe con un error de tipo antes de
    # llegar a Mongo/Kafka.
    coleccion = MagicMock()
    monkeypatch.setattr(kafka_consumer, "get_collection", lambda: coleccion)
    monkeypatch.setattr(kafka_consumer, "_get_producer", lambda: MagicMock())

    kafka_consumer._handle_ticket_created({"ticketId": "sin-descripcion", "zone": None})

    coleccion.update_one.assert_called_once()


def test_consume_loop_sigue_despues_de_un_mensaje_malformado(monkeypatch):
    # El comentario de _consume_loop promete que un solo mensaje malformado no
    # tumba el hilo consumidor para siempre. Se simula exactamente ese
    # escenario: un mensaje que no es JSON valido seguido de uno valido, y se
    # confirma que el segundo si se procesa.
    mensaje_malformado = MagicMock(value=b"esto no es JSON en absoluto")
    mensaje_valido = MagicMock(value=json.dumps({
        "ticketId": "ticket-2", "zone": "QUEVEDO_CENTRO", "description": "DNS no resuelve"
    }).encode("utf-8"))

    consumidor_falso = MagicMock()
    consumidor_falso.__iter__ = lambda self: iter([mensaje_malformado, mensaje_valido])
    monkeypatch.setattr(kafka_consumer, "KafkaConsumer", lambda *a, **k: consumidor_falso)

    llamadas = []
    monkeypatch.setattr(
        kafka_consumer, "_handle_ticket_created",
        lambda event: llamadas.append(event),
    )

    kafka_consumer._consume_loop()

    assert len(llamadas) == 1
    assert llamadas[0]["ticketId"] == "ticket-2"
