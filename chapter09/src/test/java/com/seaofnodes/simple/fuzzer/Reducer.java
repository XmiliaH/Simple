package com.seaofnodes.simple.fuzzer;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * This class is used to reduce test cases created by the fuzzer.
 * It applies rewrite rules to the test case and checks that the
 * test still produces the same exception.
 * This is done in a fixpoint iteration until every rewrite produces
 * no or a different exception.
 */
public class Reducer {

    /**
     * Holder for the rewrite rules
     */
    private record Replace(Pattern p, String r) {}

    /**
     * List of rewrite rules.
     */
    private static final Replace[] REPLACEMENTS = {
            new Replace(Pattern.compile("(?<=[+\\-*/<>=(])--"), ""),
            new Replace(Pattern.compile("\\b--"), "+"),
            new Replace(Pattern.compile("\\b\\+-"), "-"),
            new Replace(Pattern.compile("\\bfalse\\b"), "0"),
            new Replace(Pattern.compile("\\btrue\\b"), "1"),
            new Replace(Pattern.compile("-"), ""),
            new Replace(Pattern.compile("(?<![+\\-*/<>=])(?:-|\\*|/|>=|<=|>|<|!=|==)(?![+\\-*/<>=])"), "+"),
            new Replace(Pattern.compile("\\{(?:[^{}]|\\{})+}"), "{}"),
            new Replace(Pattern.compile("\\{((?:[^{}]|\\{})+)}"), "$1"),
            new Replace(Pattern.compile("\\(([^()]+)\\)"), "$1"),
            new Replace(Pattern.compile("\\b\\w+\\b"), "0"),
            new Replace(Pattern.compile("\\b(?:[^\\W0]|\\w\\w+)\\b"), "1"),
            new Replace(Pattern.compile("\\d+\\+"), ""),
            new Replace(Pattern.compile("\\+\\d+"), ""),
            new Replace(Pattern.compile("(?<=\\n|^)[^\\n]*(?=\\n|$)"), "\n"),
            new Replace(Pattern.compile("\\n+"), "\n"),
            new Replace(Pattern.compile("^\\n+|\\n+$"), ""),
            new Replace(Pattern.compile("else *"), ""),
            new Replace(Pattern.compile("\\bif\\([^()]+\\) *"), ""),
            new Replace(Pattern.compile("\\bwhile\\([^()]+\\) *"), ""),
    };

    /**
     * Predicate to test if the reduced script generates the same error.
     */
    private final Predicate<String> tester;

    /**
     * Create a reducer with a test function.
     * @param tester Test function which returns true when the same issue is found in the script.
     */
    private Reducer(Predicate<String> tester) {
        this.tester = tester;
    }

    /**
     * Rewrites the script with one rewrite rule.
     */
    private String doReplacement(String script, Pattern pattern, String with) {
        var matcher = pattern.matcher(script);
        var sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            var pos = sb.length();
            matcher.appendReplacement(sb, with);
            var tail = sb.length();
            matcher.appendTail(sb);
            if (tester.test(sb.toString())) {
                sb.setLength(tail);
            } else {
                sb.setLength(pos);
                sb.append(script, last, matcher.end());
            }
            last = matcher.end();
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Clean up variable names from the random ones generated by the fuzzer.
     */
    private String cleanVariables(String script) {
        var matcher = Pattern.compile("\\bint ([0-9a-zA-Z_]+)").matcher(script);
        int num = 0;
        while (matcher.find()) {
            var var = matcher.group(1);
            if (var.matches("v\\d+")) continue;
            while (script.contains("v"+num)) num++;
            var n = script.replaceAll("\\b"+var+"\\b", "v" + num);
            if (tester.test(n)) {
                script = n;
            }
        }
        return script;
    }

    /**
     * Run all rewrite rules once.
     */
    private String doAllReplacements(String script) {
        for (var replace: REPLACEMENTS) {
            script = doReplacement(script, replace.p, replace.r);
        }
        return script;
    }

    /**
     * Reduce the script to a minimal version which produces the same problem with a test function.
     * @param tester Test function which returns true when the same issue is found in the script.
     */
    public static String reduce(String script, Predicate<String> tester) {
        var reducer = new Reducer(tester);
        String old;
        do {
            old = script;
            script = reducer.doAllReplacements(script);
            script = reducer.cleanVariables(script);
        } while (!script.equals(old));
        return script;
    }

    /**
     * Reduce the script to a minimal version which produces the same problem with a reproducer.
     * @param ex The exception originally caused by the script
     * @param reproducer Test function which throws an exception which will be checked against ex.
     */
    public static String reduce(String script, Throwable ex, Consumer<String> reproducer) {
        return reduce(script, s->{
            Throwable nex = null;
            try {
                reproducer.accept(s);
            } catch (Throwable e) {
                nex = e;
            }
            return FuzzerUtils.isExceptionFromSameCause(nex, ex);
        });
    }

}
