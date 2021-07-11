package ru.salavatdautov;

import java.util.Date;

public class Record implements Comparable<Record> {
    public String domain = "";
    public String[] subdomains;
    public Date date;
    public byte[] login;
    public byte[] password;
    public byte[] remark;

    @Override
    public int compareTo(Record record) {
        if (this.date == null && record.date == null) {
            return 0;
        } else if (this.date == null) {
            return 1;
        } else if (record.date == null) {
            return -1;
        }
        int result = this.date.compareTo(record.date);
        if (result == 0) {
            return this.domain.compareTo(record.domain);
        }
        return result;
    }
}
