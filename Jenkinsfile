pipeline {
    agent {
        label 'local-agent'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        CI = 'true'
        COREPACK_ENABLE_STRICT = '0'
        NPM_CONFIG_PM_ON_FAIL = 'ignore'
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk-amd64'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        DB_APP_PASSWORD = 'app_dev_pwd'
        DB_OWNER_PASSWORD = 'owner_dev_pwd'
        JWT_SECRET = 'ci-only-secret-at-least-32-bytes-long!!'
    }

    stages {
        // Valida que scripts/setup-db.sql aplica en un PostgreSQL 16 limpio. Las
        // pruebas del backend no usan esta base: levantan la suya con Testcontainers.
        // El agente corre dos builds a la vez, asi que el contenedor lleva el
        // BUILD_TAG en el nombre y no publica puertos en la VM.
        stage('Prepare PostgreSQL') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    pg="ci-postgres-$(printf '%s' "$BUILD_TAG" | tr -c 'A-Za-z0-9_.-' '-')"
                    echo "[CI] Preparando contenedor de PostgreSQL 16 ($pg)..."
                    docker rm -f "$pg" 2>/dev/null || true

                    docker run -d \
                        --name "$pg" \
                        -e POSTGRES_PASSWORD=postgres \
                        postgres:16

                    echo "[CI] Esperando a que PostgreSQL complete el proceso de inicializacion..."
                    for i in $(seq 1 45); do
                        if docker logs "$pg" 2>&1 | grep -q "PostgreSQL init process complete"; then
                            echo "[CI] Inicializacion de PostgreSQL completada en el intento $i"
                            break
                        fi
                        sleep 1
                    done

                    echo "[CI] Esperando a que PostgreSQL este listo para consultas..."
                    for i in $(seq 1 45); do
                        if docker exec "$pg" pg_isready -U postgres >/dev/null 2>&1; then
                            echo "[CI] PostgreSQL listo en el intento $i"
                            break
                        fi
                        sleep 1
                    done

                    echo "[CI] Inicializando roles y bases de datos con scripts/setup-db.sql..."
                    for attempt in $(seq 1 5); do
                        if docker exec -i "$pg" psql -v ON_ERROR_STOP=1 -U postgres < scripts/setup-db.sql; then
                            echo "[CI] Base de datos inicializada exitosamente"
                            exit 0
                        fi
                        echo "[CI] Reintentando inicializacion ($attempt/5)..."
                        sleep 2
                    done
                    echo "[CI] scripts/setup-db.sql no se pudo aplicar" >&2
                    exit 1
                '''
            }
        }

        stage('Security Scan') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    echo "[CI] Ejecutando escaneo unificado de seguridad y secretos (CAL-14)..."
                    chmod +x scripts/security-scan.sh
                    ./scripts/security-scan.sh
                '''
            }
        }

        stage('Backend Quality Gates') {
            steps {
                dir('backend') {
                    sh './mvnw -B verify'
                }
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Frontend Quality Gates') {
            steps {
                dir('frontend') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        echo "[CI] Instalando dependencias de frontend con pnpm..."
                        pnpm install --frozen-lockfile

                        echo "[CI] Ejecutando linter..."
                        pnpm lint

                        echo "[CI] Comprobando tipos TypeScript..."
                        pnpm exec tsc --noEmit

                        echo "[CI] Ejecutando pruebas unitarias de frontend..."
                        pnpm test

                        echo "[CI] Compilando frontend para produccion..."
                        pnpm build
                    '''
                }
            }
        }

        stage('Mobile Quality Gates') {
            steps {
                dir('mobile') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        echo "[CI] Instalando dependencias de la app movil con pnpm..."
                        pnpm install --frozen-lockfile

                        echo "[CI] Comprobando tipos TypeScript de la app movil..."
                        pnpm exec tsc --noEmit

                        echo "[CI] Ejecutando pruebas unitarias de la app movil..."
                        pnpm test
                    '''
                }
            }
        }

        stage('Production Images') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    echo "[CI] Construyendo y verificando imagenes minimas de produccion (CAL-17)..."
                    VERIFY_TAG="verify-$(printf '%s' "$BUILD_TAG" | tr -c 'A-Za-z0-9_.-' '-')" bash scripts/verify-images.sh
                '''
            }
        }
    }

    post {
        always {
            sh '''#!/usr/bin/env bash
                echo "[CI] Limpiando contenedores e imagenes temporales de este build..."
                run_id=$(printf '%s' "$BUILD_TAG" | tr -c 'A-Za-z0-9_.-' '-')
                docker rm -f "ci-postgres-$run_id" 2>/dev/null || true
                docker rmi -f "callejon9-backend:verify-$run_id" "callejon9-frontend:verify-$run_id" 2>/dev/null || true
            '''
            cleanWs(cleanWhenAborted: true, cleanWhenFailure: true, cleanWhenNotBuilt: true, cleanWhenSuccess: true, cleanWhenUnstable: true)
        }
        success {
            echo "[CI] Todas las puertas de calidad pasaron exitosamente (CAL-89: status check SUCCESS)."
        }
        failure {
            echo "[CI] Fallo en la ejecucion del pipeline de CI (CAL-89: status check FAILURE)."
        }
    }
}
