package project.witty.keys.app.entities;

public class Country {
    private String name;
    private String phoneCode;

    public Country(String name, String phoneCode) {
        this.name = name;
        this.phoneCode = phoneCode;
    }

    public String getName() { return name; }
    public String getPhoneCode() { return phoneCode; }
}