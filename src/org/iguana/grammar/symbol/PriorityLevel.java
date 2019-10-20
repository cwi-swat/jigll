package org.iguana.grammar.symbol;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A priority group is a list of alternatives.
 */
public class PriorityLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<Alternative> alternatives;

    public static PriorityLevel from(List<Alternative> alternatives) {
        return new PriorityLevel.Builder().addAlternatives(alternatives).build();
    }

    public PriorityLevel(Builder builder) {
        this.alternatives = builder.alternatives;
    }

    public List<Alternative> getAlternatives() {
        return alternatives;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PriorityLevel)) return false;
        PriorityLevel other = (PriorityLevel) obj;
        return this.alternatives.equals(other.alternatives);
    }

    @Override
    public int hashCode() {
        return alternatives.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Alternative alternative : alternatives) {
            sb.append(alternative);
            sb.append("\n");
            sb.append("  | ");
        }
        sb.delete(sb.length() - 4, sb.length());
        return sb.toString();
    }

    public static class Builder {
        private List<Alternative> alternatives = new ArrayList<>();

        public Builder addAlternative(Alternative alternative) {
            alternatives.add(alternative);
            return this;
        }

        public Builder addAlternatives(List<Alternative> alternatives) {
            this.alternatives.addAll(alternatives);
            return this;
        }

        public PriorityLevel build() {
            return new PriorityLevel(this);
        }
    }
}