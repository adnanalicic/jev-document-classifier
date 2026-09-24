.PHONY: up down logs rebuild benchmark benchmark-shell

up:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f

rebuild:
	docker compose build --no-cache

benchmark:
	docker compose --profile benchmark run --rm benchmark

benchmark-shell:
	docker compose --profile benchmark run --rm --entrypoint sh benchmark
