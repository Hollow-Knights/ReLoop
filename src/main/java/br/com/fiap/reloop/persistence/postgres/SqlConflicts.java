package br.com.fiap.reloop.persistence.postgres;

import org.hibernate.exception.ConstraintViolationException;

final class SqlConflicts {
    private SqlConflicts() { }
    static boolean causedBy(Throwable failure, String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equals(violation.getConstraintName())) return true;
        }
        return false;
    }
}
