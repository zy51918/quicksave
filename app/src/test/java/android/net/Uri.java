package android.net;

/**
 * Minimal Uri stub for unit tests. Overrides the "not mocked" Android stub so that
 * Uri.parse() returns a real object instead of throwing UnsupportedOperationException.
 */
public class Uri {

    private final String uriString;

    private Uri(String uriString) {
        this.uriString = uriString;
    }

    public static Uri parse(String uriString) {
        return new Uri(uriString);
    }

    @Override
    public String toString() {
        return uriString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Uri)) return false;
        Uri other = (Uri) o;
        if (uriString == null) return other.uriString == null;
        return uriString.equals(other.uriString);
    }

    @Override
    public int hashCode() {
        return uriString != null ? uriString.hashCode() : 0;
    }
}
