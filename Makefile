SHELL := /bin/bash
.DEFAULT_GOAL := help

GRADLE := ./gradlew
COMPOSE_FILE := virtualization/stack/docker-compose.yml

.PHONY: help test unit cli-test core-test interceptors-test build native integration clean mock-up mock-down mock-logs lint

help: ## Show available make targets
	@grep -E '^[a-zA-Z0-9_.-]+:.*?## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS=":.*?## "} {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

lint: ## Run static checks
	$(GRADLE) check

test: ## Run the full JVM test suite
	$(GRADLE) test

unit: test ## Alias for test

cli-test: ## Run CLI module tests
	$(GRADLE) :cli:test

core-test: ## Run core module tests
	$(GRADLE) :core:test

interceptors-test: ## Run interceptors module tests
	$(GRADLE) :interceptors:test

build: ## Build all modules
	$(GRADLE) build

native: ## Build native CLI binary
	$(GRADLE) :cli:nativeCompile

integration: ## Execute the integration test harness
	./scripts/integration-test.sh

mock-up: ## Start the Jira mock server stack
	docker compose -f $(COMPOSE_FILE) up -d jira

mock-down: ## Stop the Jira mock server stack
	docker compose -f $(COMPOSE_FILE) down --remove-orphans

mock-logs: ## Tail logs from the Jira mock server
	docker compose -f $(COMPOSE_FILE) logs -f jira

clean: ## Remove build outputs
	$(GRADLE) clean
