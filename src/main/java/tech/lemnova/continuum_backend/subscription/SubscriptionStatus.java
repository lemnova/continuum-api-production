package tech.lemnova.continuum_backend.subscription;

public enum SubscriptionStatus {
    ACTIVE, // Assinatura ativa
    CANCELED, // Cancelada pelo usuário
    PAST_DUE, // Pagamento falhou
    INCOMPLETE, // Pagamento incompleto
    TRIALING, // Em período de teste
    UNPAID, // Não pago
}
