package dev.tako.papersdelight.api.item;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 不可变物品匹配表达式: 裸物品 ID, 普通标签 {@code #ns:tag} 与高级标签 {@code advtag:ns:tag}; 多个表达式之间是 any-of. */
public final class ItemMatcher {

    private static final String ADVANCED_TAG_PREFIX = "advtag:";
    private static final ItemMatcher EMPTY = new ItemMatcher(List.of());

    private final List<Term> terms;
    private final List<String> expressions;
    private final String stableKey;

    private ItemMatcher(List<Term> terms) {
        this.terms = List.copyOf(terms);
        this.expressions = this.terms.stream().map(Term::expression).toList();
        this.stableKey = this.terms.stream()
                .map(Term::canonicalKey)
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "|" + right)
                .orElse("empty");
    }

    public static ItemMatcher empty() {
        return EMPTY;
    }

    public static ItemMatcher of(@Nullable String expression) {
        Term term = parse(expression);
        return term == null ? EMPTY : new ItemMatcher(List.of(term));
    }

    public static ItemMatcher anyOf(@Nullable Collection<String> expressions) {
        if (expressions == null || expressions.isEmpty()) return EMPTY;

        Map<String, Term> uniqueTerms = new LinkedHashMap<>();
        for (String expression : expressions) {
            Term term = parse(expression);
            if (term != null) uniqueTerms.putIfAbsent(term.canonicalKey(), term);
        }
        if (uniqueTerms.isEmpty()) return EMPTY;
        return new ItemMatcher(new ArrayList<>(uniqueTerms.values()));
    }

    public <T> boolean matches(@Nullable T item, @Nullable ItemMatcherResolver<? super T> resolver) {
        if (item == null || resolver == null || terms.isEmpty()) return false;

        for (Term term : terms) {
            boolean matched = switch (term.kind()) {
                case ITEM -> resolver.matchesItem(item, term.value());
                case TAG -> resolver.matchesTag(item, term.value());
                case ADVANCED_TAG -> resolver.matchesAdvancedTag(item, term.value());
            };
            if (matched) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public List<String> expressions() {
        return expressions;
    }

    /** 与表达式书写顺序无关, 可当索引或缓存 key; 判断命中仍要用 {@link #matches}. */
    public String stableKey() {
        return stableKey;
    }

    private static @Nullable Term parse(@Nullable String rawExpression) {
        if (rawExpression == null) return null;
        String expression = rawExpression.trim().toLowerCase(Locale.ROOT);
        if (expression.isEmpty()) return null;

        if (expression.startsWith(ADVANCED_TAG_PREFIX)) {
            String value = expression.substring(ADVANCED_TAG_PREFIX.length()).trim();
            return value.isEmpty() ? null : new Term(Kind.ADVANCED_TAG, value);
        }
        if (expression.startsWith("#")) {
            String value = expression.substring(1).trim();
            return value.isEmpty() ? null : new Term(Kind.TAG, value);
        }
        return new Term(Kind.ITEM, expression);
    }

    private enum Kind {
        ITEM("i:"),
        TAG("t:"),
        ADVANCED_TAG("a:");

        private final String keyPrefix;

        Kind(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    private record Term(Kind kind, String value) {
        private String expression() {
            return switch (kind) {
                case ITEM -> value;
                case TAG -> "#" + value;
                case ADVANCED_TAG -> ADVANCED_TAG_PREFIX + value;
            };
        }

        private String canonicalKey() {
            return kind.keyPrefix + value;
        }
    }
}
