package org.codehaus.httpcache4j.cache;

import com.google.common.cache.*;
import org.codehaus.httpcache4j.HTTPRequest;
import org.codehaus.httpcache4j.HTTPResponse;
import org.codehaus.httpcache4j.annotation.Beta;
import org.codehaus.httpcache4j.util.Pair;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * <p>...some description here...</p>
 *
 * <p>Note: you must explicitly add google guava as a dependency to
 * be able to use this CacheStorage implementation.</p>
 *
 * @deprecated Will be moved outside of core and most likely be replaced by caffiene.
 * This is hightly inefficient due to the full cache scan that needs to happen when
 * invalidating. This is easily fixed, but should probably be done in the new repo.
 *
 * @author <a href="mailto:hamnis@codehaus.org">Erlend Hamnaberg</a>
 */
@Beta
public class IndexedPersistentCacheStorage implements CacheStorage, RemovalListener<Key, CacheItem> {
    private final FilePersistentCacheStorage backing;
    private final Cache<Key, CacheItem> index;
    private final CacheLoader<Key, CacheItem> loader;

    public IndexedPersistentCacheStorage(File storageDir) {
        this(storageDir, 1000);
    }

    public IndexedPersistentCacheStorage(File storageDir, int maxSize) {
        backing = new FilePersistentCacheStorage(storageDir);
        loader = mkLoader(backing::get);
        index = CacheBuilder.newBuilder().removalListener(this).maximumSize(maxSize).build(loader);
    }

    private static <K,V> CacheLoader<K, V> mkLoader(Function<K, V> f) {
        return new CacheLoader<K, V>() {
            @Override
            public V load(K key) throws Exception {
                return f.apply(key);
            }
        };
    }

    @Override
    public HTTPResponse insert(HTTPRequest request, HTTPResponse response) {
        Key key = Key.create(request, response);
        HTTPResponse inserted = backing.insert(request, response);
        index.put(key, new DefaultCacheItem(inserted));
        return inserted;
    }

    @Override
    public HTTPResponse update(HTTPRequest request, HTTPResponse response) {
        Key key = Key.create(request, response);
        HTTPResponse updated = backing.update(request, response);
        index.put(key, new DefaultCacheItem(updated));
        return updated;
    }

    @Override
    public CacheItem get(Key key) {
        try {
            return loader.load(key);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public CacheItem get(final HTTPRequest request) {
        Set<Key> keys = index.asMap().keySet();
        Stream<Key> stream = keys.stream().filter(k -> k.getURI().equals(request.getNormalizedURI()) && k.getVary().matches(request));
        Optional<Key> first = stream.findFirst();
        Optional<Pair<Key, CacheItem>> head = first.flatMap(t -> backing.getItem(request));
        if (head.isPresent()) {
            Pair<Key, CacheItem> pair = head.get();
            index.put(pair.getKey(), pair.getValue());
        }
        return null;
    }

    @Override
    public void invalidate(final URI uri) {
        Set<Key> keys = index.asMap().keySet();
        Stream<Key> filtered = keys.stream().filter(p -> p.getURI().equals(uri));

        index.invalidateAll((Iterable<Key>) filtered::iterator);
        backing.invalidate(uri);
    }

    @Override
    public void clear() {
        index.invalidateAll();
        backing.clear();
    }

    @Override
    public int size() {
        return (int) index.size();
    }

    @Override
    public Iterator<Key> iterator() {
        return index.asMap().keySet().iterator();
    }

    @Override
    public void onRemoval(RemovalNotification<Key, CacheItem> notification) {
        backing.invalidate(notification.getKey());
    }

    @Override
    public void shutdown() {
        backing.shutdown();
    }
}
