package App;

import App.models.Tasks;
import App.models.Users;
import App.utils.EmailService;
import App.utils.databaseconn;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class HelloApplication extends Application {

    private ListView<Tasks> taskList = new ListView<>();
    private ObservableList<Tasks> taskItems = FXCollections.observableArrayList();
    private ComboBox<String> filterDropdown = new ComboBox<>();
    private TextField searchBar = new TextField();
    private static Users currentUser = null;

    @Override
    public void start(Stage primaryStage) {

        HBox mainLayout = new HBox(10);
        mainLayout.setPadding(new Insets(10));

        VBox leftSide = new VBox(10);
        leftSide.setPrefWidth(400);

        searchBar.setPromptText("Search tasks...");
        searchBar.setOnKeyReleased(event -> searchTasks());

        filterDropdown.getItems().addAll("All", "Due Date (Ascending)", "Due Date (Descending)", "Priority (High to Low)", "Priority (Low to High)");
        filterDropdown.setValue("All");
        filterDropdown.setOnAction(event -> filterTasks());

        Button taskSummaryButton = new Button("📈");
        taskSummaryButton.setOnAction(event -> showTaskSummary());

        Button UserButton = new Button("👤");
        UserButton.setOnAction(event -> showSignInSignUpDialog());

        HBox filterAndSummaryBox = new HBox(10, filterDropdown, taskSummaryButton, UserButton);


        taskList.setCellFactory(lv -> new ListCell<Tasks>() {
            private final CheckBox checkBox = new CheckBox();
            private final Label label = new Label();
            private final HBox container = new HBox(checkBox, label);

            {
                label.setFont(Font.font("Consolas", 12));

                // Ensure the checkbox is clickable, but the label is not
                checkBox.setMouseTransparent(false); // Allow checkbox clicks
                label.setMouseTransparent(true); // Prevent label clicks from interfering

                HBox.setMargin(label, new Insets(0, 0, 0, 10));
            }

            @Override
            protected void updateItem(Tasks task, boolean empty) {
                super.updateItem(task, empty);

                if (empty || task == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    label.setText(String.format("%-3d[%-11s%-25sDD: %s", task.getTaskId(), task.getStatus() + "]", task.getTitle(), task.getDueDate()));

                    checkBox.setSelected(task.getStatus().equals("COMPLETED"));

                    checkBox.setOnAction(event -> updateTaskStatus(task.getTaskId(), checkBox.isSelected()));

                    setGraphic(container);
                    setText(null);
                }
            }
        });

        taskList.setItems(taskItems);

        taskList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Tasks selectedTask = taskList.getSelectionModel().getSelectedItem();
                if (selectedTask != null) {
                    displayTaskDetails(selectedTask.getTaskId());
                }
            }
        });

        taskList.setItems(taskItems);

        taskList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Tasks selectedTask = taskList.getSelectionModel().getSelectedItem();
                if (selectedTask != null) {
                    displayTaskDetails(selectedTask.getTaskId());
                }
            }
        });

        leftSide.getChildren().addAll(searchBar, filterAndSummaryBox, taskList);

        VBox rightSide = new VBox(10);
        rightSide.setPrefWidth(400);
        rightSide.setPadding(new Insets(10));

        TextField taskNameField = new TextField();
        taskNameField.setPromptText("Task Name");

        TextField taskDescriptionField = new TextField();
        taskDescriptionField.setPromptText("Task Description");

        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPromptText("Due Date");

        TextField taskTypeField = new TextField();
        taskTypeField.setPromptText("Task Type");

        Label priorityLabel = new Label("Priority:");
        ComboBox<Tasks.Priority> priorityDropdown = new ComboBox<>();
        priorityDropdown.getItems().addAll(Tasks.Priority.values());
        priorityDropdown.setValue(Tasks.Priority.MEDIUM);

        Label recurrenceLabel = new Label("Recurrence:");
        ComboBox<Tasks.Recurrence_type> recurrenceDropdown = new ComboBox<>();
        recurrenceDropdown.getItems().addAll(Tasks.Recurrence_type.values());
        recurrenceDropdown.setValue(Tasks.Recurrence_type.NEVER);

        HBox priorityBox = new HBox(5, priorityLabel, priorityDropdown);
        HBox recurrenceBox = new HBox(5, recurrenceLabel, recurrenceDropdown);

        priorityBox.setSpacing(10);
        recurrenceBox.setSpacing(10);

        HBox mainBox = new HBox(20, priorityBox, recurrenceBox);

        Label recurrenceEndDateLabel = new Label("Recurrence End Date:");
        DatePicker recurrenceEndDatePicker = new DatePicker();
        recurrenceEndDatePicker.setPromptText("Recurrence End Date");
        recurrenceEndDatePicker.setVisible(false);
        recurrenceEndDateLabel.setVisible(false);

        recurrenceDropdown.valueProperty().addListener((observable, oldValue, newValue) -> {
            boolean showInputs = !newValue.equals(Tasks.Recurrence_type.NEVER);
            recurrenceEndDatePicker.setVisible(showInputs);
            recurrenceEndDateLabel.setVisible(showInputs);
        });

        Button addButton = new Button("Add Task");
        VBox.setMargin(addButton, new Insets(15, 0, 0, 0));
        addButton.setOnAction(event -> {
            if (currentUser == null) {
                Alert signInAlert = new Alert(Alert.AlertType.WARNING);
                signInAlert.setTitle("Sign In Required");
                signInAlert.setHeaderText("You are not signed in.");
                signInAlert.setContentText("Please sign in to add a task.");
                signInAlert.showAndWait();
            } else {
                String title = taskNameField.getText().trim();
                String description = taskDescriptionField.getText().trim();
                LocalDate dueDateLocal = dueDatePicker.getValue();
                String type = taskTypeField.getText().trim();
                Tasks.Priority priority = priorityDropdown.getValue();
                char isRecurring = recurrenceDropdown.getValue() != Tasks.Recurrence_type.NEVER ? 'Y' : 'N';
                Tasks.Recurrence_type recurrenceType = recurrenceDropdown.getValue() != Tasks.Recurrence_type.NEVER ? recurrenceDropdown.getValue() : null;
                Date recurrenceEndDate = recurrenceEndDatePicker.getValue() != null ? Date.valueOf(recurrenceEndDatePicker.getValue()) : null;

                StringBuilder errorMessage = new StringBuilder("Please fill out the following fields:\n");
                boolean hasError = false;

                if (title.isEmpty()) {
                    errorMessage.append("- Task Name\n");
                    hasError = true;
                }
                if (description.isEmpty()) {
                    errorMessage.append("- Task Description\n");
                    hasError = true;
                }
                if (dueDateLocal == null) {
                    errorMessage.append("- Due Date\n");
                    hasError = true;
                }
                if (type.isEmpty()) {
                    errorMessage.append("- Task Type\n");
                    hasError = true;
                }

                if (hasError) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Warning");
                    alert.setHeaderText("Incomplete Fields");
                    alert.setContentText(errorMessage.toString());
                    alert.showAndWait();
                } else {
                    Date dueDate = Date.valueOf(dueDateLocal);

                    Tasks task = new Tasks();
                    task.getTaskDetails(currentUser.getUser_id(), title, description, dueDate, type, priority, isRecurring, recurrenceType, recurrenceEndDate);

                    refreshTaskList();
                    clearForm(taskNameField, taskDescriptionField, dueDatePicker, taskTypeField, priorityDropdown, recurrenceDropdown, recurrenceEndDatePicker);
                }
            }

        });

        Label titleLabel = new Label("Add Task");
        titleLabel.setStyle("-fx-font-size: 25px;");

        rightSide.getChildren().addAll(
                titleLabel,
                new Label("Task Name:"), taskNameField,
                new Label("Task Description:"), taskDescriptionField,
                new Label("Due Date:"), dueDatePicker,
                new Label("Task Type:"), taskTypeField,
                mainBox,
                recurrenceEndDateLabel,
                recurrenceEndDatePicker,
                addButton
        );

        mainLayout.getChildren().addAll(leftSide, rightSide);

        Scene scene = new Scene(mainLayout, 850, 500);
        primaryStage.setTitle("To-Do List Application");
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshTaskList();
    }

    private void updateTaskStatus(int taskId, boolean isCompleted) {
        Tasks t = new Tasks();
        boolean valid = t.checkOffTask(currentUser.getUser_id(), taskId, isCompleted);
        if (!valid) {
            databaseconn db = new databaseconn();
            Tasks task = db.fetchTaskById(currentUser.getUser_id(), taskId);
            Tasks dependentTask = db.fetchTaskById(currentUser.getUser_id(), task.getDependency());

            // Show an alert if the task cannot be marked as completed due to a dependency
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Task Dependency");
            Label contentLabel = new Label("Task \"" + task.getTitle() + "\" cannot be marked as complete because it depends on \"" + dependentTask.getTitle() + "\". Please complete \"" + dependentTask.getTitle() + "\" first.");
            contentLabel.setWrapText(true); // Enable text wrapping
            alert.getDialogPane().setContent(contentLabel);
            alert.getDialogPane().setMinWidth(400); // Adjust the width as needed

            alert.showAndWait();
        }
        refreshTaskList();
    }

    private void refreshTaskList() {
        Tasks t = new Tasks();
        taskItems.clear();
        if (currentUser != null) {
            taskItems.setAll(t.displayTaskList(1, currentUser.getUser_id())); // Load tasks from the database
        }
        taskList.setItems(taskItems);
    }


    private void searchTasks() {
        String keyword = searchBar.getText().toLowerCase();
        Tasks t = new Tasks();
        ObservableList<Tasks> searchResults = FXCollections.observableArrayList();

        for (Tasks task : t.displayTaskList(1, currentUser.getUser_id())) {
            if (task.getTitle().toLowerCase().contains(keyword) || task.getDescription().toLowerCase().contains(keyword)) {
                searchResults.add(task); // Add the task to the search results
            }
        }

        taskList.setItems(searchResults);
    }

    private void filterTasks() {
        Tasks t = new Tasks();
        String filter = filterDropdown.getValue();
        ArrayList<Tasks> sortedList = new ArrayList<>();

        switch (filter) {
            case "All":
                sortedList = t.displayTaskList(1, currentUser.getUser_id()); // No sorting, fetch all tasks
                break;
            case "Due Date (Ascending)":
                sortedList = t.displayTaskList(2, currentUser.getUser_id()); // Sort by due date (ascending)
                break;
            case "Due Date (Descending)":
                sortedList = t.displayTaskList(3, currentUser.getUser_id()); // Sort by due date (descending)
                break;
            case "Priority (High to Low)":
                sortedList = t.displayTaskList(4, currentUser.getUser_id()); // Sort by priority (high to low)
                break;
            case "Priority (Low to High)":
                sortedList = t.displayTaskList(5, currentUser.getUser_id()); // Sort by priority (low to high)
                break;
        }

        taskItems.setAll(sortedList);
        taskList.setItems(taskItems);
    }

    private void displayTaskDetails(int taskId) {
        Tasks t = new Tasks();
        Tasks task = t.displayTaskDetails(currentUser.getUser_id(), taskId);
        if (task != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Task Details");
            alert.setHeaderText("Task #" + task.getTaskId());
            alert.setContentText(
                    "Title: " + task.getTitle() + "\n" +
                            "Description: " + task.getDescription() + "\n" +
                            "Due Date: " + task.getDueDate() + "\n" +
                            "Type: " + task.getType() + "\n" +
                            "Priority: " + task.getPriority() + "\n" +
                            "Status: " + task.getStatus() + "\n" +
                            "Dependency: " + task.getDependency() + "\n" +
                            "Recurring: " + task.isRecurring() + "\n" +
                            "Recurrence Type: " + task.getRecurrenceType() + "\n" +
                            "Recurrence End Date: " + task.getRecurrenceEndDate()
            );

            ButtonType editButton = new ButtonType("Edit", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().add(editButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == editButton) {
                openEditTaskDialog(task);
            }

        }
    }

    private void openEditTaskDialog(Tasks task) {
        Dialog<Tasks> dialog = new Dialog<>();
        dialog.setTitle("Edit Task");
        dialog.setHeaderText("Edit Task #" + task.getTaskId());

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 50, 10, 50));

        TextField titleField = new TextField(task.getTitle());
        TextField descriptionField = new TextField(task.getDescription());
        DatePicker dueDatePicker = new DatePicker(task.getDueDate().toLocalDate());
        TextField typeField = new TextField(task.getType());
        ComboBox<Tasks.Priority> priorityComboBox = new ComboBox<>();
        priorityComboBox.getItems().addAll(Tasks.Priority.values());
        priorityComboBox.setValue(task.getPriority());
        TextField dependencyField = new TextField(String.valueOf(task.getDependency()));
        ComboBox<Tasks.Recurrence_type> recurrenceTypeComboBox = new ComboBox<>();
        recurrenceTypeComboBox.getItems().addAll(Tasks.Recurrence_type.values());
        recurrenceTypeComboBox.setValue(task.getRecurrenceType());
        DatePicker recurrenceEndDatePicker = new DatePicker(task.getRecurrenceEndDate() != null ? task.getRecurrenceEndDate().toLocalDate() : null);

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descriptionField, 1, 1);
        grid.add(new Label("Due Date:"), 0, 2);
        grid.add(dueDatePicker, 1, 2);
        grid.add(new Label("Type:"), 0, 3);
        grid.add(typeField, 1, 3);
        grid.add(new Label("Priority:"), 0, 4);
        grid.add(priorityComboBox, 1, 4);
        grid.add(new Label("Dependency:"), 0, 5);
        grid.add(dependencyField, 1, 5);
        grid.add(new Label("Recurrence Type:"), 3, 0);
        grid.add(recurrenceTypeComboBox, 4, 0);
        grid.add(new Label("Recurrence End Date:"), 3, 1);
        grid.add(recurrenceEndDatePicker, 4, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButtonType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL, deleteButtonType);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);

        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            Tasks t = new Tasks();
            boolean validUpdate = t.getUpdateTask(currentUser.getUser_id(), task.getTaskId(), titleField.getText(), descriptionField.getText(),
                    Date.valueOf(dueDatePicker.getValue()), typeField.getText(), priorityComboBox.getValue(),
                    Integer.parseInt(dependencyField.getText()),
                    recurrenceTypeComboBox.getValue() != Tasks.Recurrence_type.NEVER ? 'Y' : 'N', recurrenceTypeComboBox.getValue(),
                    recurrenceEndDatePicker.getValue() != null ? Date.valueOf(recurrenceEndDatePicker.getValue()) : null);

            if (!validUpdate) {
                event.consume(); // Prevent dialog from closing
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Invalid Task Dependency");
                alert.setContentText("The task dependency you entered creates a cycle. Please enter a valid dependency.");
                alert.showAndWait();
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return task;
            } else if (dialogButton == deleteButtonType) {
                boolean confirmDelete = showConfirmationDialog("Delete Task", "Are you sure you want to delete this task?");
                if (confirmDelete) {
                    deleteTask(task.getTaskId());
                    dialog.close();
                }
                return null;
            }
            return null;
        });

        dialog.showAndWait();
        refreshTaskList();
    }

    private boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void deleteTask(int taskId) {
        Tasks t = new Tasks();
        boolean isDeleted = t.getDeleteTaskInfo(currentUser.getUser_id(), taskId);

        if (isDeleted) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Task Deleted");
            alert.setHeaderText(null);
            alert.setContentText("Task #" + taskId + " has been deleted successfully.");
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to delete task #" + taskId + ".");
            alert.showAndWait();
        }
    }

    private void showTaskSummary() {
        if (currentUser == null) {
            Alert signInAlert = new Alert(Alert.AlertType.WARNING);
            signInAlert.setTitle("Sign In Required");
            signInAlert.setHeaderText("You are not signed in.");
            signInAlert.setContentText("No tasks found. Sign in to add a task.");
            signInAlert.showAndWait();
            return;
        }

        Tasks tasks = new Tasks();
        HashMap<String, Object> summary = tasks.getTaskSummary(currentUser.getUser_id());

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Task Summary");
        alert.setHeaderText("Task Summary");

        VBox summaryBox = new VBox(10);
        summaryBox.setPadding(new Insets(10));

        Label totalTasksLabel = new Label("Total Tasks: " + summary.get("Total Tasks"));
        Label completedLabel = new Label("Completed: " + summary.get("Completed"));
        Label pendingLabel = new Label("Pending: " + summary.get("Pending"));
        Label completionRateLabel = new Label(String.format("Completion Rate: %.2f%%", summary.get("Completion Rate")));

        HashMap<String, Integer> categories = (HashMap<String, Integer>) summary.get("Task Categories");
        StringBuilder categoriesText = new StringBuilder("Task Categories: \n");
        for (Map.Entry<String, Integer> entry : categories.entrySet()) {
            categoriesText.append(entry.getKey()).append(": ").append(entry.getValue()).append("  ");
        }
        Label categoriesLabel = new Label(categoriesText.toString());

        summaryBox.getChildren().addAll(totalTasksLabel, completedLabel, pendingLabel, completionRateLabel, categoriesLabel);

        alert.getDialogPane().setContent(summaryBox);
        alert.getDialogPane().setMinWidth(300);

        alert.showAndWait();
    }


    private void clearForm(TextField taskNameField, TextField taskDescriptionField, DatePicker dueDatePicker, TextField taskTypeField, ComboBox<Tasks.Priority> priorityDropdown, ComboBox<Tasks.Recurrence_type> recurrenceDropdown, DatePicker recurrenceEndDatePicker) {
        taskNameField.clear();
        taskDescriptionField.clear();
        dueDatePicker.setValue(null);
        taskTypeField.clear();
        priorityDropdown.setValue(Tasks.Priority.MEDIUM);
        recurrenceDropdown.setValue(Tasks.Recurrence_type.NEVER);
        recurrenceEndDatePicker.setValue(null);
        recurrenceEndDatePicker.setVisible(false);
    }

    private void showSignInSignUpDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("User Details");

        VBox dialogContent = new VBox(10);

        if (currentUser == null) {
            Label titleLabel = new Label("User Details");
            titleLabel.setStyle("-fx-font-size: 25px;");
            Label messageLabel = new Label("You are not signed in.");
            dialogContent.getChildren().addAll(titleLabel, messageLabel);

            ButtonType signUpButtonType = new ButtonType("Sign Up", ButtonBar.ButtonData.OK_DONE);
            ButtonType signInButtonType = new ButtonType("Sign In", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(signInButtonType, signUpButtonType, ButtonType.CANCEL);

            dialog.setResultConverter(buttonType -> {
                if (buttonType == signUpButtonType) {
                    showSignUpDialog();
                } else if (buttonType == signInButtonType) {
                    showSignInDialog();
                }
                return null;
            });
        } else {
            Label titleLabel = new Label("User Details");
            titleLabel.setStyle("-fx-font-size: 25px;");
            Label messageLabel = new Label("Signed in as: " + currentUser.getEmail());
            dialogContent.getChildren().addAll(titleLabel, messageLabel);

            ButtonType logOutButtonType = new ButtonType("Log Out", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(logOutButtonType, ButtonType.CANCEL);

            dialog.setResultConverter(buttonType -> {
                if (buttonType == logOutButtonType) {
                    currentUser = null;
                    refreshTaskList();
                }
                return null;
            });
        }

        dialog.getDialogPane().setContent(dialogContent);
        dialog.showAndWait();
    }

    private void showSignUpDialog() {
        Users user = new Users();
        Dialog<Users> dialog = new Dialog<>();
        dialog.setTitle("Sign Up");
        dialog.setHeaderText("Sign Up");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 50, 10, 50));

        TextField emailField = new TextField();
        TextField passwordField = new TextField();

        grid.add(new Label("Email:"), 0, 0);
        grid.add(emailField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType signUpButtonType = new ButtonType("Sign Up", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signUpButtonType, ButtonType.CANCEL);

        Button signUpButton = (Button) dialog.getDialogPane().lookupButton(signUpButtonType);

        signUpButton.setOnAction(event -> {
            String email = emailField.getText();
            String password = passwordField.getText();

            if (email.isEmpty() || password.isEmpty()) {
                showAlert("Error", "Email and password cannot be empty.");
                event.consume();
                return;
            }

            user.signUp(email, password);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setContentText("You've signed up successfully! Please sign in to activate your new account!");
            alert.showAndWait();
            dialog.close();
            showSignInDialog();
        });

        dialog.showAndWait();
        refreshTaskList();

    }

    private void showSignInDialog() {
        Users user = new Users();
        Dialog<Users> dialog = new Dialog<>();
        dialog.setTitle("Sign In");
        dialog.setHeaderText("Sign In");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 50, 10, 50));

        TextField emailField = new TextField();
        TextField passwordField = new TextField();

        grid.add(new Label("Email:"), 0, 0);
        grid.add(emailField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType signInButtonType = new ButtonType("Sign In", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signInButtonType, ButtonType.CANCEL);

        Button signInButton = (Button) dialog.getDialogPane().lookupButton(signInButtonType);

        signInButton.setOnAction(event -> {
            String email = emailField.getText();
            String password = passwordField.getText();

            if (email.isEmpty() || password.isEmpty()) {
                showAlert("Error", "Email and password cannot be empty.");
                return;
            }

            currentUser = user.signIn(email, password);

            if (currentUser != null) {
                dialog.setResult(currentUser);
                dialog.close();
                EmailService emailService = new EmailService();
                emailService.startScheduler(currentUser.getUser_id());
                System.out.println("Scheduler started for user: " + currentUser.getEmail());
                showSignInSignUpDialog();
            } else {
                showAlert("Error", "Incorrect email or password.");
            }
        });

        dialog.showAndWait();
        refreshTaskList();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        databaseconn.createTable();
        launch(args);
    }
}