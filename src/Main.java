import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

public class Main {
    static Set<String> visitedSites = Collections.synchronizedSet(new HashSet<>());
    static ConcurrentLinkedQueue<String> sitesQueue = new ConcurrentLinkedQueue<>();
    //static LinkedBlockingDeque<String> sitesQueue2 = new LinkedBlockingDeque<>(10_000);
    static Set<EmailEntry> emails = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws InterruptedException, IOException {
        String startUrl = "https://touro.edu".toLowerCase();

        //Set<String> sitesToVisit = Collections.synchronizedSet(new HashSet<>());
        //sitesToVisit.add(startUrl);

        sitesQueue.offer(startUrl);
        //sitesQueue2.offer(startUrl);

        final Pattern SITE_PATTERN = Pattern.compile("href=\"https?://\\S*(?:\\.com|\\.edu|\\.gov|\\.org|\\.net)\\S*\"", Pattern.CASE_INSENSITIVE);
        final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+(?:(com)|org|edu|gov|net)", Pattern.CASE_INSENSITIVE);
        final int NUM_EMAILS = 10_000;
        //AtomicInteger websiteCounter = new AtomicInteger(0);
        //AtomicInteger emailCounter = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newCachedThreadPool()) {
            while (emails.size() < NUM_EMAILS) {
                executor.execute(() -> {
                    String theURL = sitesQueue.poll();
                    //String theURL = sitesQueue2.poll();
                    while (visitedSites.contains(theURL)){
                        theURL = sitesQueue.poll();
                        //theURL = sitesQueue2.poll();
                    }
                    visitedSites.add(theURL);
                    //sitesToVisit.remove(theURL);
                    if (!Objects.equals(theURL, null)) {
                        try {
                            //Document doc = Jsoup.connect(theURL).userAgent("Mozilla").get();
                            //String title = doc.title();
                            //System.out.println(title);
                            //System.out.printf("Thread: %s; Website #%d: %s\n", Thread.currentThread().getName(), websiteCounter.incrementAndGet(), theURL);
                            //Element body = doc.body();
                            //String html = body.outerHtml();
                            //String html = doc.outerHtml();
                            String html2 = Jsoup.connect(theURL).userAgent("Mozilla").get().body().outerHtml();
                            //int websiteNumber = websiteCounter.incrementAndGet();
                            //System.out.println("Website #: " + websiteNumber);

                            Matcher matcher = SITE_PATTERN.matcher(html2);
                            while (matcher.find()) {
                                String urlLong = matcher.group();
                                String url = urlLong.substring(6, urlLong.length() - 1).toLowerCase();
                                if (!visitedSites.contains(url)){// && !sitesToVisit.contains(url)) {
                                    sitesQueue.offer(url);
                                    //sitesQueue2.offer(url);
                                    //sitesToVisit.add(url);
                                }
                            }

                            matcher = EMAIL_PATTERN.matcher(html2);
                            while (matcher.find() && emails.size() < NUM_EMAILS) {
                                //String emailAddress = matcher.group().toLowerCase();
                                int id = emails.size() + 1; //emailCounter.incrementAndGet();
                                //EmailEntry email = new EmailEntry(id, matcher.group().toLowerCase(), theURL, new Timestamp(System.currentTimeMillis()));
                                if (emails.add(new EmailEntry(id, matcher.group().toLowerCase(), theURL, new Timestamp(System.currentTimeMillis())))) {
                                    System.out.printf("Email #%,d on website #%,d\n", id, visitedSites.size());
                                    //System.out.printf("Email #%d: %s\n", id, emailAddress);
                                    //System.out.printf("From thread %s and website #%d:\n\t%s\n", Thread.currentThread().getName(), visitedSites.size(), email);
                                }
                            }
                        } catch (HttpStatusException e) {
                            //System.out.printf("Error Status Code %d for URL: %s\n", e.getStatusCode(), e.getUrl());
                        } catch (UnsupportedMimeTypeException e) {
                            //System.out.printf("Unsupported Mime Type %s for URL %s\n", e.getMimeType(), e.getUrl());
                        } catch (OutOfMemoryError e) {
                            System.out.println(e.getMessage());
                            //System.out.printf("%s %s; Visited Sites: %,d; Sites Queue Size: %,d; Number of Emails %,d\n", Thread.currentThread().getName(), e.getMessage(), visitedSites.size(), sitesQueue.size(), emails.size());
                        } catch (Exception e) {
                            //System.out.println(e.getMessage());
                        }
                    }
                    if (emails.size() == NUM_EMAILS){
                        executor.shutdownNow();
                    }
                });
                if (emails.size() == NUM_EMAILS){
                    boolean terminated = executor.awaitTermination(1, TimeUnit.MILLISECONDS);
                    if (terminated){
                        System.out.println(true);
                    }
                }
            }
        }

//        System.out.println();
//        for (EmailEntry e : emails){
//            System.out.println(e);
//        }

        Properties credentials = new Properties();
        credentials.load(new FileInputStream("credentials.properties"));
        String endpoint = credentials.getProperty("db_connection");
        String database = credentials.getProperty("database");
        String username = credentials.getProperty("user");
        String password = credentials.getProperty("password");

        String connectionUrl =
                "jdbc:sqlserver://" + endpoint + ";"
                        + "database=" + database + ";"
                        + "user=" + username + ";"
                        + "password=" + password + ";"
                        + "encrypt=true;"
                        + "trustServerCertificate=true;"
                        + "loginTimeout=30;";

        try (Connection connection = DriverManager.getConnection(connectionUrl);
             Statement statement = connection.createStatement())
        {
            StringBuilder sqlCommandBuilder = new StringBuilder();
            sqlCommandBuilder.append("TRUNCATE TABLE Emails;\nInsert INTO Emails VALUES ");
            for (EmailEntry e : emails){
                sqlCommandBuilder.append(String.format("(%d, '%s', '%s', '%s'),", e.getId(), e.getEmailAddress(), e.getSourceUrl(), e.getTimeStamp()));
            }
            sqlCommandBuilder.deleteCharAt(sqlCommandBuilder.length() - 1);
            sqlCommandBuilder.append(";");
            statement.execute(sqlCommandBuilder.toString());
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}