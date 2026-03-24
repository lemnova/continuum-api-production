package tech.lemnova.continuum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ContinuumApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContinuumApplication.class, args);
    }
}

// =============================================================================
// FIM — v11_Backend_Final.java
//
// MongoDB collections ativas (SOMENTE fonte de verdade operacional):
//   users                      ← auth, vaultId, plano, contadores de limite
//   subscriptions              ← billing Stripe
//   stripe_event_log           ← idempotência de webhooks
//   email_verification_tokens  ← tokens de verificação de e-mail
//
// Backblaze B2 — vault privado por usuário (TODO o dado de uso):
//   _notes/{id}.md             ← conteúdo markdown das notas
//   _notes/index.json          ← NoteIndex[] (título, folderId, timestamps)
//   _entities/entities.json    ← Entity[] (definições completas)
//   _entities/entity_index.json← métricas de menções por entidade
//   _folders/folders.json      ← Folder[] (estrutura de pastas)
//   _tracking/events.json      ← TrackingEvent[] (check-ins)
//   _refs/refs.json            ← NoteReference[] (menções indexadas)
//
// NOTA IMPORTANTE — concorrência:
//   Todos os arquivos JSON no B2 são sobrescritos por inteiro a cada operação
//   (read-modify-write). Para usuários com grande volume de dados, considerar:
//   - Debounce nas operações async (ex: 1 rebuild/seg por vault)
//   - Redis como cache intermediário dos JSONs para reduzir leituras do B2
//   - Migrar _refs e _tracking para arquivos particionados por mês
// =============================================================================
