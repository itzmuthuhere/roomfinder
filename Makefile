.PHONY: db db-stop db-reset api web dev test psql

db:            ## start Postgres + PostGIS
	docker compose up -d db

db-stop:
	docker compose stop db

db-reset:      ## wipe local data and start clean
	docker compose down -v && docker compose up -d db

psql:
	docker exec -it roomfinder-db psql -U roomfinder -d roomfinder

api:           ## run Spring Boot on :8080
	cd api && ./gradlew bootRun

web:           ## run Vite dev server on :5173
	cd app && npm run dev

test:
	cd api && ./gradlew test

dev: db        ## db + api + web together
	@echo "DB up. Run 'make api' in one terminal and 'make web' in another."
