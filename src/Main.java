import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        String startUrl = "https://touro.edu".toLowerCase();
        Set<String> visitedSites = Collections.synchronizedSet(new HashSet<>());
        Set<String> sitesToVisit = Collections.synchronizedSet(new HashSet<>());
        sitesToVisit.add(startUrl);
        //List<Object> sitesQueue = Collections.synchronizedList(new ArrayList<>());
        Queue<String> sitesQueue = new LinkedList<>();
        sitesQueue.offer(startUrl);
        Set<EmailEntry> emails = Collections.synchronizedSet(new HashSet<>());
        Document doc;
        String title;
        Element body;
        String html;
        final Pattern SITE_PATTERN = Pattern.compile("href=\"https?://\\S*(?:\\.com|\\.edu|\\.gov|\\.org|\\.net)\\S*\"", Pattern.CASE_INSENSITIVE);
        final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+(?:(com)|org|edu|gov|net)", Pattern.CASE_INSENSITIVE);
        Matcher matcher;
        String urlLong;
        String url;
        String emailAddress;
        Timestamp timestamp;
        int emailCounter = 0;
        int websiteCounter = 0;
        final int NUM_EMAILS = 100;

        while (emails.size() < NUM_EMAILS && sitesQueue.peek() != null){
            startUrl = sitesQueue.poll();
            visitedSites.add(startUrl);
            sitesToVisit.remove(startUrl);
            try {
                doc = Jsoup.connect(startUrl).userAgent("Mozilla").get();
                title = doc.title();
                System.out.println(title);
                System.out.printf("Website #%d: %s\n", ++websiteCounter, startUrl);
                body = doc.body();
                html = body.outerHtml();

                matcher = SITE_PATTERN.matcher(html);
                while (matcher.find()){
                    urlLong = matcher.group();
                    url = urlLong.substring(6, urlLong.length() - 1).toLowerCase();
                    if (!visitedSites.contains(url) && !sitesToVisit.contains(url)){
                        sitesQueue.add(url);
                        sitesToVisit.add(url);
                    }
                }

                matcher = EMAIL_PATTERN.matcher(html);
                while (matcher.find() && emails.size() < NUM_EMAILS){
                    emailAddress = matcher.group().toLowerCase();
                    timestamp = new Timestamp(System.currentTimeMillis());
                    if (emails.add(new EmailEntry(emailAddress, startUrl, timestamp))){
                        System.out.printf("Email #%d: %s\n", ++emailCounter, emailAddress);
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
        System.out.println();
        for (EmailEntry e : emails){
            System.out.println(e);
        }

//        doc = Jsoup.connect(startUrl).userAgent("Mozilla").get();
//        title = doc.title();
//        System.out.println(title);
//        body = doc.body();
//        html = body.outerHtml();
//        System.out.println(html);
//
//        matcher = SITE_PATTERN.matcher(html);
//        while (matcher.find()){
//            urlLong = matcher.group();
//            url = urlLong.substring(6, urlLong.length() - 1);
//            if (!visitedSites.contains(url) && sitesToVisit.add(url)){
//                sitesQueue.add(url);
//            }
//        }
//        for (Object s : sitesQueue){
//            System.out.println(s);
//        }
//
//        matcher = EMAIL_PATTERN.matcher(html);
//        while (matcher.find() && emails.size() < 10){
//            emails.add(matcher.group());
//        }
//        System.out.println(emails);
    }
}