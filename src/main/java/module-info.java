module com.example.todolistappfinal {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.mail;


    opens App to javafx.fxml;
    exports App;
}