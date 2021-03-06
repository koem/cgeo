package cgeo.geocaching.export;

import static org.assertj.core.api.Assertions.assertThat;

import cgeo.CGeoTestCase;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.LogEntry;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LogType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ExportTest extends CGeoTestCase {

    public static void testGSAKExport() {
        final Geocache cache = new Geocache();
        cache.setGeocode("GCX1234");
        final LogEntry log = new LogEntry(1353244820000L, LogType.FOUND_IT, "Hidden in a tree");
        final FieldNotes fieldNotes = new FieldNotes();
        fieldNotes.add(cache, log);
        assertEquals("Non matching export " + fieldNotes.getContent(), "GCX1234,2012-11-18T13:20:20Z,Found it,\"Hidden in a tree\"\n", fieldNotes.getContent());
    }

    public static void testGpxExportSmilies() throws InterruptedException, ExecutionException, IOException {
        final Geocache cache = new Geocache();
        cache.setGeocode("GCX1234");
        cache.setCoords(new Geopoint("N 49 44.000 E 8 37.000"));
        final LogEntry log = new LogEntry(1353244820000L, LogType.FOUND_IT, "Smile: \ud83d\ude0a");
        DataStore.saveCache(cache, LoadFlags.SAVE_ALL);
        DataStore.saveLogs(cache.getGeocode(), Collections.singletonList(log));
        assertCanExport(cache);
    }

    public static void testGpxExportUnknownConnector() throws InterruptedException, ExecutionException, IOException {
        final Geocache cache = new Geocache();
        cache.setGeocode("ABC123");
        cache.setCoords(new Geopoint("N 49 44.000 E 8 37.000"));
        DataStore.saveCache(cache, LoadFlags.SAVE_ALL);

        assertThat(ConnectorFactory.getConnector(cache).getName()).isEqualTo("Unknown caches");
        assertCanExport(cache);
    }

    private static void assertCanExport(final Geocache cache) throws InterruptedException, ExecutionException, IOException {
        // enforce storing in database, as GPX will not take information from cache
        cache.setDetailed(true);
        DataStore.saveCache(cache, LoadFlags.SAVE_ALL);

        final List<Geocache> exportList = Collections.singletonList(cache);
        final GpxExportTester gpxExport = new GpxExportTester();
        File result = null;
        try {
            result = gpxExport.testExportSync(exportList);
        } finally {
            DataStore.removeCache(cache.getGeocode(), LoadFlags.REMOVE_ALL);
        }

        assertThat(result).isNotNull();

        // make sure we actually exported waypoints
        final String gpx = org.apache.commons.io.FileUtils.readFileToString(result);
        assertThat(gpx).contains("<wpt");
        assertThat(gpx).contains(cache.getGeocode());
        if (cache.getUrl() != null) {
            assertThat(gpx).contains("<url>");
        } else {
            assertThat(gpx).doesNotContain("<url>");
        }

        FileUtils.deleteIgnoringFailure(result);
    }

    private static class GpxExportTester extends GpxExport {

        public File testExportSync(final List<Geocache> caches) throws InterruptedException, ExecutionException {
            final ArrayList<String> geocodes = new ArrayList<>(caches.size());
            for (final Geocache cache : caches) {
                geocodes.add(cache.getGeocode());
            }
            final ExportTask task = new ExportTask(null);
            task.execute(geocodes.toArray(new String[geocodes.size()]));
            return task.get();
        }

    }

}
