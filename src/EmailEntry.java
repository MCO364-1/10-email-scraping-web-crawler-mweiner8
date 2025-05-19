import java.sql.Timestamp;
import java.util.Objects;

public class EmailEntry {
    private final String emailAddress;
    private final String sourceUrl;
    private final Timestamp timeStamp;

    public EmailEntry(String email, String url, Timestamp time){
        emailAddress = email;
        sourceUrl = url;
        timeStamp = time;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Timestamp getTimeStamp() {
        return timeStamp;
    }

    @Override
    public String toString(){
        return String.format("Email: %s;\tSource: %s;\tTime Received: %s", emailAddress, sourceUrl, timeStamp);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EmailEntry that = (EmailEntry) o;
        return Objects.equals(emailAddress.toLowerCase(), that.emailAddress.toLowerCase());
    }

    @Override
    public int hashCode() {
        return emailAddress.hashCode();
    }
}