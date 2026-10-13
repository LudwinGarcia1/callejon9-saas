pipeline {
    agent {
        label 'local-agent'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        CI = 'true'
        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk-amd64'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        DB_PORT = '5432'
        DB_HOST = '127.0.0.1'
        DB_APP_PASSWORD = 'app_dev_pwd'
        DB_OWNER_PASSWORD = 'owner_dev_pwd'
        JWT_SECRET = 'ci-only-secret-at-least-32-bytes-long!!'
    }

    stages {
        stage('Prepare PostgreSQL') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    echo "[CI] Preparando contenedor de PostgreSQL 16..."
                    docker rm -f ci-postgres 2>/dev/null || true

                    docker run -d \
                        --name ci-postgres \
                        -p 5432:5432 \
                        -e POSTGRES_PASSWORD=postgres \
                        postgres:16

                    echo "[CI] Esperando a que PostgreSQL este listo para consultas..."
                    for i in $(seq 1 45); do
                        if docker exec ci-postgres psql -U postgres -c "SELECT 1" >/dev/null 2>&1; then
                            echo "[CI] PostgreSQL listo en el intento $i"
                            break
                        fi
                        sleep 1
                    done

                    echo "[CI] Inicializando roles y bases de datos con scripts/setup-db.sql..."
                    docker exec -i ci-postgres psql -U postgres < scripts/setup-db.sql
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
    }

    post {
        always {
            sh '''#!/usr/bin/env bash
                echo "[CI] Limpiando contenedor de base de datos..."
                docker rm -f ci-postgres 2>/dev/null || true
            '''
            cleanWs(cleanWhenAborted: true, cleanWhenFailure: true, cleanWhenNotBuilt: true, cleanWhenSuccess: true, cleanWhenUnstable: true)
        }
    }
}
