package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiles a YAML string as a case-insensitive regex; an invalid regex is a mapping error. */
class CaseInsensitivePatternDeserializer extends StdScalarDeserializer<Pattern> {

    CaseInsensitivePatternDeserializer() {
        super(Pattern.class);
    }

    @Override
    public Pattern deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        String regex = parser.getValueAsString();
        if (regex == null) {
            return (Pattern) ctxt.handleUnexpectedToken(Pattern.class, parser);
        }
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException e) {
            throw ctxt.weirdStringException(regex, Pattern.class, "invalid regex: " + e.getDescription());
        }
    }
}
