package io.github.quanghung304.jobhunter.source.weworkremotely;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;

/**
 * Parses a WWR category feed. The XML is read once with a hardened JDOM builder (no DOCTYPE,
 * no external entities); Rome maps the standard RSS fields from that document, and WWR's own
 * item elements are read from it directly, because Rome does not expose un-namespaced extras.
 */
final class WeWorkRemotelyFeedParser {

    private WeWorkRemotelyFeedParser() {
    }

    /** @throws IllegalArgumentException if the text is not a readable RSS feed */
    static List<WeWorkRemotelyItemDto> parse(String xml) {
        Document document = readSafely(xml);
        SyndFeed feed;
        try {
            feed = new SyndFeedInput().build(document.clone());
        } catch (FeedException | IllegalArgumentException e) {
            throw new IllegalArgumentException("not an RSS feed: " + e.getMessage(), e);
        }
        Element channel = document.getRootElement().getChild("channel");
        List<Element> items = channel == null ? List.of() : channel.getChildren("item");
        List<SyndEntry> entries = feed.getEntries();
        if (entries.size() != items.size()) {
            throw new IllegalStateException("Rome returned " + entries.size() + " entries for " + items.size() + " items");
        }
        var result = new ArrayList<WeWorkRemotelyItemDto>(items.size());
        for (int i = 0; i < items.size(); i++) {
            result.add(toDto(entries.get(i), items.get(i)));
        }
        return result;
    }

    private static WeWorkRemotelyItemDto toDto(SyndEntry entry, Element item) {
        return new WeWorkRemotelyItemDto(
                entry.getTitle(),
                entry.getLink(),
                entry.getUri(),
                entry.getDescription() == null ? null : entry.getDescription().getValue(),
                entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant(),
                entry.getCategories().stream().map(SyndCategory::getName).toList(),
                text(item, "region"),
                text(item, "country"),
                text(item, "state"),
                text(item, "skills"),
                text(item, "type"),
                text(item, "expires_at"),
                toJson(item));
    }

    /** Child elements by qualified name; repeated names become arrays, attributes "@name". */
    static ObjectNode toJson(Element item) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        for (Element child : item.getChildren()) {
            String name = child.getQualifiedName();
            var value = child.getAttributes().isEmpty()
                    ? JsonNodeFactory.instance.textNode(child.getTextTrim())
                    : withAttributes(child);
            if (json.has(name)) {
                var existing = json.get(name);
                var array = existing.isArray() ? (ArrayNode) existing
                        : json.putArray(name).add(existing);
                array.add(value);
            } else {
                json.set(name, value);
            }
        }
        return json;
    }

    private static ObjectNode withAttributes(Element element) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (Attribute attribute : element.getAttributes()) {
            node.put("@" + attribute.getQualifiedName(), attribute.getValue());
        }
        if (!element.getTextTrim().isEmpty()) {
            node.put("#text", element.getTextTrim());
        }
        return node;
    }

    private static String text(Element item, String name) {
        Element child = item.getChild(name);
        if (child == null) {
            return null;
        }
        String value = child.getTextTrim();
        return value.isEmpty() ? null : value;
    }

    private static Document readSafely(String xml) {
        var builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        builder.setExpandEntities(false);
        try {
            return builder.build(new StringReader(xml));
        } catch (JDOMException | IOException e) {
            throw new IllegalArgumentException("unreadable XML: " + e.getMessage(), e);
        }
    }
}
