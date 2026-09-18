"""get_classification (app/main.py) no tenia ninguna prueba: es el unico endpoint
que expone el resultado guardado por kafka_consumer, y decide entre devolver el
documento o lanzar 404 segun si Mongo encontro algo -- ese if/else es exactamente
la clase de logica que se invierte por error sin que nadie lo note. Se llama la
funcion directo (no hace falta TestClient/httpx, que ni siquiera esta en
requirements.txt) con la misma tecnica de monkeypatch que ya usa
test_kafka_consumer.py."""

from unittest.mock import MagicMock

import pytest
from fastapi import HTTPException

from app import main


def test_health_responde_up():
    assert main.health() == {"status": "UP"}


def test_get_classification_devuelve_el_documento_cuando_existe(monkeypatch):
    coleccion = MagicMock()
    coleccion.find_one.return_value = {
        "ticketId": "0fb2c5be-3e80-46aa-af79-f2aa597f8e4a",
        "category": "CONECTIVIDAD",
        "priority": "CRITICO",
    }
    monkeypatch.setattr(main, "get_collection", lambda: coleccion)

    resultado = main.get_classification("0fb2c5be-3e80-46aa-af79-f2aa597f8e4a")

    assert resultado["category"] == "CONECTIVIDAD"
    coleccion.find_one.assert_called_once_with(
        {"ticketId": "0fb2c5be-3e80-46aa-af79-f2aa597f8e4a"}, {"_id": 0}
    )


def test_get_classification_nunca_devuelve_el_campo_id_crudo_de_mongo(monkeypatch):
    # La proyeccion {"_id": 0} es la que evita filtrar un ObjectId de Mongo
    # (no serializable a JSON tal cual) al cliente HTTP.
    coleccion = MagicMock()
    coleccion.find_one.return_value = {"ticketId": "t1", "category": "OTRO"}
    monkeypatch.setattr(main, "get_collection", lambda: coleccion)

    main.get_classification("t1")

    _filtro, proyeccion = coleccion.find_one.call_args.args
    assert proyeccion == {"_id": 0}


def test_get_classification_lanza_404_cuando_no_hay_clasificacion_todavia(monkeypatch):
    coleccion = MagicMock()
    coleccion.find_one.return_value = None
    monkeypatch.setattr(main, "get_collection", lambda: coleccion)

    with pytest.raises(HTTPException) as excinfo:
        main.get_classification("ticket-sin-clasificar")

    assert excinfo.value.status_code == 404
    assert "ticket-sin-clasificar" in excinfo.value.detail
