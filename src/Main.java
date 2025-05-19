import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        String startUrl = "https://touro.edu".toLowerCase();
        Set<String> visitedSites = Collections.synchronizedSet(new HashSet<>());
        Set<String> sitesToVisit = Collections.synchronizedSet(new HashSet<>());
        sitesToVisit.add(startUrl);
        ConcurrentLinkedQueue<String> sitesQueue = new ConcurrentLinkedQueue<>();
        sitesQueue.offer(startUrl);
        Set<EmailEntry> emails = Collections.synchronizedSet(new HashSet<>());
        final Pattern SITE_PATTERN = Pattern.compile("href=\"https?://\\S*(?:\\.com|\\.edu|\\.gov|\\.org|\\.net)\\S*\"", Pattern.CASE_INSENSITIVE);
        final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+(?:(com)|org|edu|gov|net)", Pattern.CASE_INSENSITIVE);
        final int NUM_EMAILS = 100; //TODO do it with 10_000 to complete the HW
        AtomicInteger websiteCounter = new AtomicInteger(0);
        AtomicInteger emailCounter = new AtomicInteger(0);

        Thread[] threads = new Thread[5];
        int counter = 0;

        while (emails.size() < NUM_EMAILS){
            threads[counter] = new Thread(() -> {
                String theURL = sitesQueue.poll();
                visitedSites.add(theURL);
                sitesToVisit.remove(theURL);
                if (!Objects.equals(theURL, null) && emails.size() < NUM_EMAILS){
                    try {
                        Document doc = Jsoup.connect(theURL).userAgent("Mozilla").get();
                        String title = doc.title();
                        System.out.println(title);
                        System.out.printf("Website #%d: %s\n", websiteCounter.incrementAndGet(), theURL);
                        Element body = doc.body();
                        String html = body.outerHtml();

                        Matcher matcher = SITE_PATTERN.matcher(html);
                        while (matcher.find()){
                            String urlLong = matcher.group();
                            String url = urlLong.substring(6, urlLong.length() - 1).toLowerCase();
                            if (!visitedSites.contains(url) && !sitesToVisit.contains(url)){
                                sitesQueue.add(url);
                                sitesToVisit.add(url);
                            }
                        }

                        matcher = EMAIL_PATTERN.matcher(html);
                        while (matcher.find() && emails.size() < NUM_EMAILS){
                            String emailAddress = matcher.group().toLowerCase();
                            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                            if (emails.add(new EmailEntry(emailAddress, theURL, timestamp))){
                                System.out.printf("Email #%d: %s\n", emailCounter.incrementAndGet(), emailAddress);
                            }
                        }
                    } catch (HttpStatusException e){
                        System.out.printf("Error Status Code %d for URL: %s\n", e.getStatusCode(), e.getUrl());
                    } catch (UnsupportedMimeTypeException e){
                        System.out.printf("Unsupported Mime Type %s for URL %s\n", e.getMimeType(), e.getUrl());
                    } catch (Exception e){
                        System.out.println(e.getMessage());
                    }
                }
            });
            threads[counter].start();
            threads[counter].join();
            counter++;
            if (counter == threads.length){
                counter = 0;
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
            int id = 1;
            for (EmailEntry e : emails){
                sqlCommandBuilder.append(String.format("(%d, '%s', '%s', '%s'),", id++, e.getEmailAddress(), e.getSourceUrl(), e.getTimeStamp()));
            }
            sqlCommandBuilder.deleteCharAt(sqlCommandBuilder.length() - 1);
            sqlCommandBuilder.append(";");
            statement.execute(sqlCommandBuilder.toString());
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}