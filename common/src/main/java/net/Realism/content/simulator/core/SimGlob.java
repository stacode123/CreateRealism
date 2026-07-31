package net.Realism.content.simulator.core;

import java.util.regex.Pattern;

/**
 * Station-name glob matching, mirroring Create's use of catnip
 * {@code Glob.toRegexPattern}: {@code *} matches anything, everything else is
 * literal, whole-string match.
 */
public class SimGlob {

    public static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(".*");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0)
            regex.append(Pattern.quote(literal.toString()));
        return Pattern.compile(regex.toString());
    }
}
