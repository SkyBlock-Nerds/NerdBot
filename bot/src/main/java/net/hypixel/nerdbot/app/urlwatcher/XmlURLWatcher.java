package net.hypixel.nerdbot.app.urlwatcher;

import net.hypixel.nerdbot.core.Tuple;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * URL watcher specialized for XML endpoints (e.g., RSS/Atom).
 * It avoids any JSON parsing and returns an empty diff list by default.
 * Subclasses can implement richer XML-aware diffs if desired.
 */
public class XmlURLWatcher extends URLWatcher {

    public XmlURLWatcher(String url) {
        super(url);
    }

    public XmlURLWatcher(String url, Map<String, String> headers) {
        super(url, headers);
    }

    protected XmlURLWatcher(String url, Map<String, String> headers, boolean loadInitialContent) {
        super(url, headers, loadInitialContent);
    }

    @Override
    protected List<Tuple<String, Object, Object>> computeChangedValues(String oldContent, String newContent) {
        // No-op: Hypixel thread watching uses its own XML parsing and does not rely on diffs.
        return Collections.emptyList();
    }
}