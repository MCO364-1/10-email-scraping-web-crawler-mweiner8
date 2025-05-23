import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

public class EmailEntry implements Serializable {
    private final int id;
    private final String emailAddress;
    private final String sourceUrl;
    private final Timestamp timeStamp;

    public EmailEntry(int id, String email, String url, Timestamp time){
        this.id = id;
        emailAddress = email;
        sourceUrl = url;
        timeStamp = time;
    }

    public int getId() {
        return id;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceUrlSql(){
        int index = sourceUrl.indexOf('\'');
        if (index > -1){
            return sourceUrl.replaceAll("'", "''");
        }
        return sourceUrl;
    }

    public Timestamp getTimeStamp() {
        return timeStamp;
    }

    @Override
    public String toString(){
        return String.format("ID: %d; Email: %s;\tSource: %s;\tTime Received: %s", id, emailAddress, sourceUrl, timeStamp);
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