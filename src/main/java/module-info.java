module com.example.todolistappfinal {
    requires org.slf4j;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.mail;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires com.fasterxml.jackson.databind;
    requires java.sql;


    opens App to javafx.fxml;
    exports App;
}