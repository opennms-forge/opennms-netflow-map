package org.opennms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.data.JSONArray;
import processing.data.JSONObject;

public class OnmsNetflowMap extends PApplet {
    @Option(name = "-h", usage = "elastic search host")
    private String elasticSearchHost = "localhost";

    @Option(name = "-p", usage = "elastic search port")
    private int elasticSearchPort = 9200;

    @Option(name = "-a", usage = "public address to use when resolving private networks")
    private String localAddress = "193.174.29.55";

    private final String WORLD_FILENAME = "world.txt";
    private final long DELAY = 250;

    private PGraphics backgroundImage;
    private final List<Curve> curves = new ArrayList<>();
    private DatabaseReader databaseReader;
    private double stepSize = 5.0;
    private long lastTimestamp = System.currentTimeMillis();

    private CityResponse localResponse;

    {
        try {
            databaseReader = new DatabaseReader.Builder(OnmsNetflowMap.class.getClassLoader().getResourceAsStream("GeoLite2-City.mmdb")).build();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            localResponse = databaseReader.city(InetAddress.getByName(localAddress));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeoIp2Exception e) {
            e.printStackTrace();
        }
    }

    private class Polygon {
        private int npoints = 0;
        private double xpoints[] = new double[1];
        private double ypoints[] = new double[1];

        public Polygon() {
        }

        public Polygon(final double... doubles) {
            npoints = doubles.length / 2;

            if (npoints > xpoints.length) {
                xpoints = PApplet.expand(xpoints, npoints);
                ypoints = PApplet.expand(ypoints, npoints);
            }

            for (int i = 0; i < doubles.length / 2; i++) {
                xpoints[i] = doubles[i];
                ypoints[i] = doubles[i + 1];
            }
        }

        public void addPoint(final double x, final double y) {
            if (npoints <= xpoints.length) {
                xpoints = PApplet.expand(xpoints, npoints + 1);
                ypoints = PApplet.expand(ypoints, npoints + 1);
            }

            xpoints[npoints] = x;
            ypoints[npoints] = y;
            npoints++;
        }
    }

    private class Curve {
        private final CityResponse srcResponse;
        private final CityResponse dstResponse;
        private final double impactSize;
        private int pct = 0;

        public Curve(final CityResponse srcResponse, final CityResponse dstResponse, final double impactSize) {
            this.srcResponse = srcResponse;
            this.dstResponse = dstResponse;
            this.impactSize = impactSize;
            this.pct = 0;
        }

        public boolean draw() {
            int x1 = (int) ((srcResponse.getLocation().getLongitude() + 180.0) / 360.0 * width);
            int y1 = (int) (height - (srcResponse.getLocation().getLatitude() + 90.0) / 180.0 * height);
            int x2 = (int) ((dstResponse.getLocation().getLongitude() + 180.0) / 360.0 * width);
            int y2 = (int) (height - (dstResponse.getLocation().getLatitude() + 90.0) / 180.0 * height);
            boolean done = draw(x1, y1, x2, y2, this.pct);
            this.pct += stepSize;
            return done;
        }

        boolean draw(float x1, float y1, float x2, float y2, float pct) {
            float deltaX = x2 - x1;
            float deltaY = y2 - y1;
            float fooX = deltaX < 0 ? -1 : 1;
            float fooY = deltaY < 0 ? -1 : 1;

            while (PApplet.abs(deltaX) > width / 2) {
                deltaX = (width - deltaX) % width;
                fooX = -1;
            }

            while (PApplet.abs(deltaY) > height / 2) {
                deltaY = (height - deltaY) % height;
                fooY = -1;
            }

            float b = PApplet.max(PApplet.abs(deltaX), PApplet.abs(deltaY));

            int impact = 50;
            float alpha = 192;

            fill(255, alpha);

            if (pct > b) {
                if (pct > b + impact) {
                    alpha = 255 - (pct - (b + impact)) * 3;

                    if (alpha < 0) {
                        noFill();

                        return true;
                    } else {
                        fill(255, alpha);
                    }

                    double i = impactSize * width / 160 - (pct - (b + impact)) * 0.25;

                    if (i > 0) {
                        ellipse(x2, y2, (float) i, (float) i);
                    }
                } else {
                    ellipse(x2, y2, (float) (impactSize * width / 160), (float) (impactSize * width / 160));
                }
            }

            textSize(12);
            final String srcCity = srcResponse.getCity() != null ? srcResponse.getCity().getName() : "Unknown";
            final String dstCity = dstResponse.getCity() != null ? dstResponse.getCity().getName() : "Unknown";

            fill(0, alpha);
            text(srcCity != null ? srcCity : "Unknown", 2 + x1 + 1, 10 + y1 + 1);
            text(dstCity != null ? dstCity : "Unknown", 2 + x2 + 1, 10 + y2 + 1);

            fill(255, alpha);
            text(srcCity != null ? srcCity : "Unknown", 2 + x1, 10 + y1);
            text(dstCity != null ? dstCity : "Unknown", 2 + x2, 10 + y2);

            for (float a = 0; a < pct; a++) {
                float x = x1 + fooX * PApplet.abs(deltaX) * a / b;
                float p = PApplet.sin(a / b * PConstants.PI) * (PApplet.abs(deltaX) / 5);
                float y = y1 + fooY * PApplet.abs(deltaY) * a / b - p;
                x = (x < 0 ? x + width : x) % width;
                y = (y < 0 ? y + height : y) % height;

                if (a % 10 < 5) {
                    ellipse(x, y, 1, 2);
                }

                if (a == b) {
                    break;
                }
            }

            return false;
        }
    }


    public void settings() {
        size(1280, 700);
    }

    public void setup() {
        background(0);
        noStroke();

        backgroundImage = createBackground();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    queryElasticSearchForFlows();
                    try {
                        Thread.sleep(DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void draw() {
        image(backgroundImage, 0, 0, width, height);
        for (int i = curves.size() - 1; i >= 0; i--) {
            final Curve curve = curves.get(i);
            if (curve != null && curve.draw()) {
                curves.remove(curve);
            }
        }
        stepSize = (curves.size()) / 100;
    }

    private void queryElasticSearchForFlows() {
        final JSONObject jsonObject = request(lastTimestamp);
        final JSONArray array = jsonObject.getJSONObject("hits").getJSONArray("hits");
        final int flowCount = array.size();

        for (int i = 0; i < flowCount; i++) {
            final JSONObject object = array.getJSONObject(i).getJSONObject("_source");

            lastTimestamp = Math.max(lastTimestamp, object.getLong("@timestamp"));
            InetAddress srcAddress = null;
            InetAddress dstAddress = null;
            try {
                srcAddress = InetAddress.getByName(object.getString("netflow.src_addr"));
                dstAddress = InetAddress.getByName(object.getString("netflow.dst_addr"));
            } catch (UnknownHostException e) {
                continue;
            }

            final double impactSize = Math.log(object.getInt("netflow.bytes")) * 0.25;

            if (srcAddress.isLoopbackAddress() || dstAddress.isLoopbackAddress()) {
                continue;
            }

            try {
                final CityResponse srcResponse = srcAddress.isSiteLocalAddress() ? localResponse : databaseReader.city(srcAddress);
                final CityResponse dstResponse = dstAddress.isSiteLocalAddress() ? localResponse : databaseReader.city(dstAddress);

                if (srcResponse.getLocation().getLongitude() != dstResponse.getLocation().getLongitude() || srcResponse.getLocation().getLatitude() != dstResponse.getLocation().getLatitude()) {
                    final Curve c = new Curve(srcResponse, dstResponse, impactSize);
                    curves.add(c);
                }
            } catch (IOException | GeoIp2Exception e) {
                continue;
            }
        }
    }

    private JSONObject request(final long last) {
        final String data = "{\"query\":{\"range\":{\"@timestamp\":{\"gte\":\"" + last + "\",\"lt\":\"now\"}}}}";
        final DefaultHttpClient httpClient = new DefaultHttpClient();

        try {
            final HttpPost httpPost = new HttpPost("http://" + elasticSearchHost + ":" + elasticSearchPort + "/_search?size=1000");
            httpPost.setEntity(new StringEntity(data));
            httpPost.addHeader("Content-Type", "application/json");
            final HttpResponse response = httpClient.execute(httpPost);
            return parseJSONObject(EntityUtils.toString(response.getEntity()));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
        return null;
    }

    private PGraphics createBackground() {
        final PGraphics pGraphics = createGraphics(width, height);
        pGraphics.beginDraw();
        pGraphics.fill(0);
        pGraphics.rect(0, 0, width, height);

        final List<Polygon> polygons = loadWorldMap();
        for (final Polygon p : polygons) {
            if (p.npoints < 1) {
                continue;
            }

            pGraphics.noFill();
            pGraphics.stroke(0, 255, 0);
            pGraphics.beginShape();
            for (int i = 0; i < p.npoints; i++) {
                pGraphics.vertex((int) (p.xpoints[i] * width), (int) (p.ypoints[i] * height));
            }
            pGraphics.endShape(CLOSE);
        }
        pGraphics.filter(BLUR);
        pGraphics.endDraw();

        return pGraphics;
    }

    private List<Polygon> loadWorldMap() {
        final ArrayList<Polygon> polygons = new ArrayList<Polygon>();
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(OnmsNetflowMap.class.getClassLoader().getResourceAsStream(WORLD_FILENAME)));
            while (reader.ready()) {
                final String line = reader.readLine();
                final String arr[] = line.split(",");
                final int len = arr.length / 2;
                final Polygon polygon = new Polygon();
                for (int i = 0; i < len; i++) {
                    polygon.addPoint(Double.parseDouble(arr[i * 2]), Double.parseDouble(arr[i * 2 + 1]));
                }
                polygons.add(polygon);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return polygons;
    }

    public static void main(String... args) {
        final OnmsNetflowMap onmsNetflowMap = new OnmsNetflowMap();
        final CmdLineParser parser = new CmdLineParser(onmsNetflowMap);
        try {
            parser.parseArgument(args);

            PApplet.runSketch(new String[]{"OpenNMSNetflowMap"}, onmsNetflowMap);
        } catch (CmdLineException e) {
            e.printStackTrace();
        }
    }
}
