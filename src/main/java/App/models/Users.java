package App.models;

import App.utils.databaseconn;

public class Users {
    private String email;
    private String password;
    private int user_id;

    public Users() {}

    public Users(int user_id, String email, String password) {
        this.user_id = user_id;
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public int getUser_id() { return user_id; }

    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setUser_id(int user_id) { this.user_id = user_id; }

    public void signUp(String email, String password) {
        databaseconn db = new databaseconn();

        if (db.fetchUserByEmail(email) == null) {
            setEmail(email);
            setPassword(password);
            db.insertUser(getEmail(), getPassword());
        } else {
            System.out.println("Email already exists");
        }

    }

    public Users signIn(String email, String password) {
        databaseconn db = new databaseconn();
        Users user = db.fetchUserByEmail(email);
        if (user != null && user.getPassword().equals(password)) {
            System.out.println("Login successful!");
        } else {
            user = null;
            System.out.println("Login unsuccessful!");
        }
        return user;
    }
}
