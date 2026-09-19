# Pruebas de carga con Locust (Modulo D, Guia de Entrega 4). Los dos escenarios exactos
# que exige la rubrica:
#   (i)  50 usuarios durante 5 minutos           -> ver locust.conf / uso abajo
#   (ii) rampa progresiva de 0 a 200 usuarios en 10 minutos
#
# Ambos apuntan al API Gateway (punto de entrada unico, ver services/api-gateway), nunca
# directo a un microservicio -- asi la carga observada en el dashboard de Grafana
# (panel 6: "latencia end-to-end") es la misma ruta que sigue un cliente real.
#
# Uso (desde la raiz del repositorio, con el stack levantado via docker compose):
#
#   Escenario (i) -- 50 usuarios, 5 minutos:
#     locust -f tests/load/locustfile.py --host http://localhost:8000 \
#            --users 50 --spawn-rate 10 --run-time 5m --headless \
#            --html resultados/locust/locust_escenario1.html --csv resultados/locust/locust_escenario1
#
#   Escenario (ii) -- rampa de 0 a 200 en 10 minutos:
#     locust -f tests/load/locustfile.py --host http://localhost:8000 \
#            --users 200 --spawn-rate 0.33 --run-time 10m --headless \
#            --html resultados/locust/locust_escenario2.html --csv resultados/locust/locust_escenario2
#
# (la ruta real que usan las 5 repeticiones de cada escenario esta en
# docs/experimentos/evaluacion_iso25010.md, que si escribe dentro de resultados/locust/ -- una
# version anterior de este comentario omitia el subdirectorio "locust/", lo que habria dejado
# la salida fuera de donde SHA256SUMS.txt y analizar_resultados.py la esperan)
#     (spawn-rate 0.33 usuario/seg ~ 200 usuarios repartidos en 600s, la "rampa progresiva")
#
# Requiere una cuenta CLIENTE ya creada (cliente@test.com / Passw0rd!, la que crea
# start-all.ps1 / db-init) -- cada usuario virtual inicia sesion una sola vez (on_start)
# y reutiliza el mismo access token para todas sus peticiones, igual que un cliente real.

import random

from locust import HttpUser, task, between


class ClienteConsolaUser(HttpUser):
    """Simula un CLIENTE navegando su propia consola: inicia sesion, revisa su lista de
    tickets repetidamente y, ocasionalmente, crea una solicitud nueva -- el mismo patron
    de uso real que produce apps/web contra el API Gateway."""

    wait_time = between(1, 3)

    def on_start(self):
        response = self.client.post(
            "/api/v1/auth/login",
            json={"email": "cliente@test.com", "password": "Passw0rd!"},
            name="/api/v1/auth/login",
        )
        if response.status_code == 200:
            self.token = response.json()["data"]["accessToken"]
        else:
            self.token = None

    def _auth_headers(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    @task(6)
    def listar_mis_tickets(self):
        if not self.token:
            return
        self.client.get("/api/v1/tickets", headers=self._auth_headers(), name="/api/v1/tickets [GET lista]")

    @task(1)
    def crear_ticket(self):
        if not self.token:
            return
        zona = random.choice(["QUEVEDO_CENTRO", "QUEVEDO_NORTE", "QUEVEDO_SUR"])
        self.client.post(
            "/api/v1/tickets",
            headers=self._auth_headers(),
            json={
                "zone": zona,
                "title": "Carga Locust",
                "description": "Ticket generado por la prueba de carga (Modulo D, Entrega 4)",
            },
            name="/api/v1/tickets [POST crear]",
        )


class AdminConsolaUser(HttpUser):
    """Simula un ADMIN abriendo la consola sin filtros -- exactamente el camino de
    findAll() (ver Evidencia de rendimiento del EV-AUT-03 de Carpio) que motivo agregar
    un limite explicito; esta carga sirve para confirmar que ya no degrada."""

    wait_time = between(2, 5)

    def on_start(self):
        response = self.client.post(
            "/api/v1/auth/login",
            json={"email": "admin@soporte.local", "password": "Admin123!"},
            name="/api/v1/auth/login",
        )
        self.token = response.json()["data"]["accessToken"] if response.status_code == 200 else None

    @task
    def ver_todos_los_tickets(self):
        if not self.token:
            return
        self.client.get(
            "/api/v1/tickets",
            headers={"Authorization": f"Bearer {self.token}"},
            name="/api/v1/tickets [GET admin, sin filtro]",
        )
