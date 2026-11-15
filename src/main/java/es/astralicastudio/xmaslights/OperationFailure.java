package es.astralicastudio.xmaslights;

/** Expected, localizable refusal; distinct from unexpected platform exceptions. */
final class OperationFailure extends IllegalStateException {
    OperationFailure(String messageKey) { super(messageKey); }
}
