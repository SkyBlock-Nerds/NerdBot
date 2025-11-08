package net.hypixel.nerdbot.app.urlwatcher;

import net.hypixel.nerdbot.core.JsonUtils;
import net.hypixel.nerdbot.core.Tuple;

import java.util.List;
import java.util.Map;

/**
 * URL watcher specialized for JSON endpoints. Computes key-path diffs between
 * the previous and current payload using JsonUtils.
 */
public class JsonURLWatcher extends URLWatcher {

    public JsonURLWatcher(String url) {
        super(url);
    }

    public JsonURLWatcher(String url, Map<String, String> headers) {
        super(url, headers);
    }

    protected JsonURLWatcher(String url, Map<String, String> headers, boolean loadInitialContent) {
        super(url, headers, loadInitialContent);
    }

    @Override
    protected List<Tuple<String, Object, Object>> computeChangedValues(String oldContent, String newContent) {
        return JsonUtils.findChangedValues(
            JsonUtils.parseStringToMap(oldContent),
            JsonUtils.parseStringToMap(newContent),
            ""
        );
    }
}