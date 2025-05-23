import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

public class Main {
    static Set<String> visitedSites = Collections.synchronizedSet(new HashSet<>());
    static ConcurrentLinkedQueue<String> sitesQueue = new ConcurrentLinkedQueue<>();
    static Set<EmailEntry> emails = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws IOException {
        String startUrl = "https://touro.edu".toLowerCase();
        sitesQueue.offer(startUrl);

        final Pattern SITE_PATTERN = Pattern.compile("href=\"https?://\\S*(?:\\.com|\\.edu|\\.gov|\\.org|\\.net)\\S*\"", Pattern.CASE_INSENSITIVE);
        final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+(?:(com)|org|edu|gov|net)", Pattern.CASE_INSENSITIVE);
        final int NUM_EMAILS = 10000;
        //AtomicInteger websiteCounter = new AtomicInteger(0);
        //AtomicInteger emailCounter = new AtomicInteger(0);

        LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        try (ExecutorService pool = new ThreadPoolExecutor(8, 10, 0L, TimeUnit.MILLISECONDS, taskQueue)){
            while (emails.size() < NUM_EMAILS) {
                if (taskQueue.size() < 100){
                    pool.execute(() -> {
                        if (taskQueue.isEmpty() && emails.size() == NUM_EMAILS){
                            System.out.println("is this printed?");
                            //taskQueue.clear();
                        }
                        String theURL = sitesQueue.poll();
                        while (visitedSites.contains(theURL)){
                            theURL = sitesQueue.poll();
                        }
                        visitedSites.add(theURL);
                        if (!Objects.equals(theURL, null)) {
                            try {
                                String html2 = Jsoup.connect(theURL).userAgent("Mozilla").get().body().outerHtml();
                                //int websiteNumber = websiteCounter.incrementAndGet();
                                //System.out.println("Website #: " + websiteNumber);

                                Matcher matcher = SITE_PATTERN.matcher(html2);
                                while (matcher.find() && emails.size() < NUM_EMAILS) {
                                    String urlLong = matcher.group();
                                    String url = urlLong.substring(6, urlLong.length() - 1).toLowerCase();
                                    if (!visitedSites.contains(url)){
                                        sitesQueue.offer(url);
                                    }
                                }

                                matcher = EMAIL_PATTERN.matcher(html2);
                                while (matcher.find() && emails.size() < NUM_EMAILS) {
                                    int id = emails.size() + 1;
                                    if (emails.add(new EmailEntry(id, matcher.group().toLowerCase(), theURL, new Timestamp(System.currentTimeMillis())))) {
                                        System.out.println("Email #" + id + " on website #" + visitedSites.size() + " from thread: " + Thread.currentThread().getName());
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
                                System.out.println(e.getMessage() + " " + Thread.currentThread().getName());
                            }
                        }
                        if (emails.size() == NUM_EMAILS){
                            System.out.println(Thread.currentThread().getName() + " " + sitesQueue.size());
                            sitesQueue.clear();
                            taskQueue.clear();
                            pool.shutdownNow();
                        }
                    });
                }

                if (emails.size() == NUM_EMAILS){
                    System.out.println("hit this test " + Thread.currentThread().getName());
                    sitesQueue.clear();
                    taskQueue.clear();
                }
            }
            System.out.println("waiting");
            pool.shutdownNow();
        }

//        System.out.println();
//        for (EmailEntry e : emails){
//            System.out.println(e);
//        }

        System.out.println("About to write to file");

        String filePath = "emails.dat";

        try (
                FileOutputStream fileOut = new FileOutputStream(filePath);
                BufferedOutputStream bufferOut = new BufferedOutputStream(fileOut);
                ObjectOutputStream objectOut = new ObjectOutputStream(bufferOut)
        ) {
            for (EmailEntry e : emails){
                objectOut.writeObject(e);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("---------------------------------------");

//        int num = 0;
//        try (
//                FileInputStream fileIn = new FileInputStream(filePath);
//                BufferedInputStream bufferIn = new BufferedInputStream(fileIn);
//                ObjectInputStream objectIn = new ObjectInputStream(bufferIn)
//        ) {
//            EmailEntry email;
//            while (!Objects.equals((email = (EmailEntry) objectIn.readObject()), null)){
//                System.out.println(++num + " " + email);
//            }
//        } catch (IOException | ClassNotFoundException e) {
//            System.out.println("Error " + e.getMessage());
//        }
//        System.out.println(num);

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
            try (
                    FileInputStream fileIn = new FileInputStream(filePath);
                    BufferedInputStream bufferIn = new BufferedInputStream(fileIn);
                    ObjectInputStream objectIn = new ObjectInputStream(bufferIn)
            ) {
                statement.execute("TRUNCATE TABLE Emails;");
                EmailEntry email;
                for (int i = 0; i < NUM_EMAILS / 10; i++) {
                    StringBuilder sqlCommandBuilder = new StringBuilder();
                    sqlCommandBuilder.append("Insert INTO Emails VALUES ");
                    for (int j = 0; j < NUM_EMAILS / 10; j++) {
                        email = (EmailEntry) objectIn.readObject();
                        sqlCommandBuilder.append(String.format("(%d, '%s', '%s', '%s'),", email.getId(), email.getEmailAddress(), email.getSourceUrlSql(), email.getTimeStamp()));
                    }
                    sqlCommandBuilder.deleteCharAt(sqlCommandBuilder.length() - 1);
                    sqlCommandBuilder.append(";");
                    //System.out.println(sqlCommandBuilder);
                    statement.execute(sqlCommandBuilder.toString());
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}