package App.models;

import App.utils.EmailService;
import App.utils.databaseconn;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.sql.*;

public class Tasks {
    private int task_id;
    private String title;
    private String description;
    private Date due_date;
    private String type;
    private Priority priority;
    private int dependency = 0;
    private char is_recurring;
    private Recurrence_type recurrence_type = Recurrence_type.NEVER;
    private Date recurrence_end_date = null;
    private String status = "PENDING";
    private static HashMap<Integer, Tasks> taskMap = new HashMap<>();

    // Enums

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    public enum Recurrence_type {
        NEVER, DAILY, WEEKLY, MONTHLY, YEARLY
    }

    public Tasks(){}

    public Tasks(int task_id, String title, String description, Date due_date, String type, String status, Tasks.Priority priority, int dependency, char is_recurring, Tasks.Recurrence_type recurrence_type, Date recurrence_end_date){
        this.task_id = task_id;
        this.title = title;
        this.description = description;
        this.due_date = due_date;
        this.type = type;
        this.status = status;
        this.priority = priority;
        this.dependency = dependency;
        this.is_recurring = is_recurring;
        this.recurrence_type = recurrence_type;
        this.recurrence_end_date = recurrence_end_date;
        taskMap.put(task_id, this);
    }

    //Getters
    public int getTaskId() { return task_id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Date getDueDate() { return due_date; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public int getDependency() { return dependency; }
    public char isRecurring() { return is_recurring; }
    public Recurrence_type getRecurrenceType() { return recurrence_type; }
    public Date getRecurrenceEndDate() { return recurrence_end_date; }

    //Setters
    public void setTaskId(int task_id) { this.task_id = task_id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setDueDate(Date due_date) { this.due_date = due_date; updateStatus(); }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public void setDependency(int dependency) { this.dependency = dependency; }
    public void setRecurring(char is_recurring) { this.is_recurring = is_recurring; }
    public void setRecurrenceType(Recurrence_type recurrence_type) { this.recurrence_type = recurrence_type; }
    public void setRecurrenceEndDate(Date recurrence_end_date) { this.recurrence_end_date = recurrence_end_date; }

//    CHECK TIME RELATED CONDITIONS
    private void updateStatus() {
        LocalDate currentDate = LocalDate.now(); // Get the current date and time
        if (due_date.toLocalDate().isBefore(currentDate)) {
            this.status = "OVERDUE"; // If due date is in the past, set status to "Overdue"
        } else {
            this.status = "PENDING"; // If due date is in the future, set status to "Pending"
        }
    }

    public void checkDueDateAndNotify(String recipient) {
        if (!this.status.equals("COMPLETED") && (this.due_date != null)) {

            LocalDate currentDate = LocalDate.now();
            LocalDate dueDate = this.due_date.toLocalDate();

            if (dueDate.isEqual(currentDate) || (dueDate.isAfter(currentDate) && dueDate.isBefore(currentDate.plusDays(2)))) {
                String subject = "Task Due Reminder";
                String body = "Your task '" + this.title + "' is due within 24 hours. Please complete it soon!";
                System.out.println("Sending email to: " + recipient);
                EmailService.sendEmail(recipient, subject, body);
            } else if (dueDate.isBefore(currentDate)) {
                String subject = "Overdue Task Reminder";
                String body = "Your task '" + this.title + "' is overdue! Please complete it soon!";
                System.out.println("Sending email to: " + recipient);
                EmailService.sendEmail(recipient, subject, body);
            }
        }
    }

    public void createNextRecurrence(int user_id, Tasks completedTask) {

        LocalDate nextDueDate = calculateNextDueDate(completedTask.getDueDate().toLocalDate(), completedTask.getRecurrenceType());

        boolean isNotOngoing = (completedTask.isRecurring() == 'N') || (completedTask.getRecurrenceEndDate() != null && nextDueDate.isAfter(completedTask.getRecurrenceEndDate().toLocalDate()));

        if (!isNotOngoing) {
            Tasks newTask = completedTask;
            newTask.setDueDate(Date.valueOf(nextDueDate));

            databaseconn db = new databaseconn();
            db.insertTask(user_id, newTask.getTitle(), newTask.getDescription(), newTask.getDueDate(), newTask.getType(), newTask.getStatus(), newTask.getPriority(), newTask.getDependency(), newTask.isRecurring(), newTask.getRecurrenceType(), newTask.getRecurrenceEndDate());

            String email = db.fetchEmailByID(user_id);
            checkDueDateAndNotify(email);
        }
    }

    private LocalDate calculateNextDueDate(LocalDate currentDueDate, Recurrence_type recurrenceType) {
        switch (recurrenceType) {
            case DAILY:
                return currentDueDate.plusDays(1);
            case WEEKLY:
                return currentDueDate.plusWeeks(1);
            case MONTHLY:
                return currentDueDate.plusMonths(1);
            case YEARLY:
                return currentDueDate.plusYears(1);
            default:
                throw new IllegalArgumentException("Invalid recurrence type.");
        }
    }

//    CHECK FOR CYCLE
    public boolean hasCycle() {
        Tasks slow = this;
        Tasks fast = this;

        while (fast != null && fast.getDependency() != 0) {
            slow = taskMap.get(slow.getDependency());
            if (slow == null || slow.getDependency() == 0) {
                return false;
            }

            fast = taskMap.get(fast.getDependency());
            if (fast == null || fast.getDependency() == 0) {
                return false;
            }

            fast = taskMap.get(fast.getDependency());

            if (slow != null && fast != null && slow.task_id == fast.task_id) {
                return true;
            }
        }
        return false;
    }

    public boolean setDependencyWithCycleCheck(int dependency) {
        if (dependency == this.task_id) {
            System.out.println("Error: Task cannot depend on itself.");
            return false;
        }

        if (!taskMap.containsKey(dependency)) {
            System.out.println("Error: Dependency task does not exist.");
            return false;
        }

        int oldDependency = this.dependency;
        this.dependency = dependency;

        if (hasCycle()) {
            System.out.println("Error: This dependency would create a cycle. Please enter a valid dependency.");
            this.dependency = oldDependency;
            return false;
        } else {
            System.out.println("Dependency set successfully!");
            return true;
        }
    }

//    GET TASKS INFO
    public void getTaskDetails(int user_id, String title_in, String description_in, Date due_date_in, String type_in, Priority priority_in, char is_recurring_in, Recurrence_type recurrence_type_in, Date recurrence_end_date_in) {
        databaseconn db = new databaseconn();

        setTitle(title_in);
        setDescription(description_in);
        setDueDate(due_date_in);
        setType(type_in);
        setPriority(priority_in);
        setRecurring(is_recurring_in);
        if (is_recurring_in == 'Y') {
            setRecurrenceType(recurrence_type_in);
            setRecurrenceEndDate(recurrence_end_date_in);
        }

        db.insertTask(user_id, getTitle(), getDescription(), getDueDate(), getType(), getStatus(), getPriority(), getDependency(), isRecurring(), getRecurrenceType(), getRecurrenceEndDate());
        String email = db.fetchEmailByID(user_id);
        checkDueDateAndNotify(email);
    }


    public boolean getUpdateTask(int user_id, int taskId, String newTitle, String newDescription, Date newDueDate, String newType, Priority newPriority, int newDependency, char newIsRecurring, Recurrence_type newRecurrence_type, Date newEndDate){
        databaseconn db = new databaseconn();
        db.loadTasksFromDatabase(taskMap, user_id);

        Tasks task = db.fetchTaskById(user_id, taskId);
        task.setTitle(newTitle);
        task.setDescription(newDescription);
        task.setDueDate(newDueDate);
        task.setType(newType);
        task.setPriority(newPriority);
        Tasks task_hash = taskMap.get(taskId);
        if (newDependency != 0) {
            if (task_hash.setDependencyWithCycleCheck(newDependency)) {
                task.setDependency(newDependency);
            } else {
                return false;
            }
        } else {
            task.setDependency(newDependency);
        }
        task.setRecurring(newIsRecurring);
        if (newIsRecurring == 'Y') {
            task.setRecurrenceType(newRecurrence_type);
            task.setRecurrenceEndDate(newEndDate);
        }
        db.updateTask(task.getTitle(), task.getDescription(), task.getDueDate(), task.getType(), task.getStatus(), task.getPriority(), task.getDependency(), task.isRecurring(), task.getRecurrenceType(), task.getRecurrenceEndDate(), taskId, user_id);
        taskMap.put(task.getTaskId(), task);

        String email = db.fetchEmailByID(user_id);
        checkDueDateAndNotify(email);

        return true;
    }

//    COMPLETE / DELETE TASKS
    public boolean checkOffTask(int user_id, int taskId, boolean isCompleted) {
        databaseconn db = new databaseconn();
        Tasks task = db.fetchTaskById(user_id, taskId);
        if (isCompleted) {
            if (task.getDependency() != 0) {
                Tasks dependentTask = db.fetchTaskById(user_id, task.getDependency());
                if (!dependentTask.getStatus().equalsIgnoreCase("COMPLETED")) {
                    return false;
                }
            }
            task.setStatus("COMPLETED");
            db.updateTask(task.getTitle(), task.getDescription(), task.getDueDate(), task.getType(), task.getStatus(), task.getPriority(), task.getDependency(), task.isRecurring(), task.getRecurrenceType(), task.getRecurrenceEndDate(), taskId, user_id);
            if (task.isRecurring() == 'Y') {
                createNextRecurrence(user_id, task);
            }
            return true;
        } else {
            LocalDate currentDate = LocalDate.now();
            LocalDate dueDate = task.getDueDate().toLocalDate();
            if (currentDate.isBefore(dueDate) || currentDate.isEqual(dueDate)) {
                task.setStatus("PENDING");
            } else {
                task.setStatus("OVERDUE");
            }
            db.updateTask(task.getTitle(), task.getDescription(), task.getDueDate(), task.getType(), task.getStatus(), task.getPriority(), task.getDependency(), task.isRecurring(), task.getRecurrenceType(), task.getRecurrenceEndDate(), taskId, user_id);
            return true;
        }

    }

    public boolean getDeleteTaskInfo(int user_id, int taskId) {
        databaseconn db = new databaseconn();
        Tasks task = db.fetchTaskById(user_id, taskId);
        taskMap.remove(taskId);
        return db.deleteTask(taskId, task.getTitle());
    }

//    DISPLAY TASK DETAILS
    public ArrayList<Tasks> displayTaskList(int choice, int user_id) {
        databaseconn db = new databaseconn();
        ArrayList<Tasks> list = db.fetchTasksFromDatabase(user_id);
        switch (choice) {
            case 1:
                break;
            case 2:
                Collections.sort(list, Comparator.comparing(Tasks::getDueDate));
                break;
            case 3:
                Collections.sort(list, Comparator.comparing(Tasks::getDueDate).reversed());
                break;
            case 4:
                Collections.sort(list, Comparator.comparing(Tasks::getPriority).reversed());
                break;
            case 5:
                Collections.sort(list, Comparator.comparing(Tasks::getPriority));
                break;
        }
        return list;

    }

    public Tasks displayTaskDetails(int user_id, int taskID) {
        databaseconn db = new databaseconn();
        Tasks task = db.fetchTaskById(user_id, taskID);
        return task;
    }

    public HashMap<String, Object> getTaskSummary(int user_id) {
        databaseconn db = new databaseconn();
        db.loadTasksFromDatabase(taskMap, user_id);

        int totalTasks = taskMap.size();

        int completed = 0;
        int incomplete = 0;
        for (Tasks task : taskMap.values()) {
            if (task.getStatus().equals("COMPLETED")) {
                completed++;
            } else {
                incomplete++;
            }
        }

        double completionRate = totalTasks == 0 ? 0 : (double) completed / totalTasks * 100;

        HashMap<String, Integer> categorySummary = new HashMap<>();
        for (Tasks task : taskMap.values()) {
            String type = task.getType().toUpperCase();
            categorySummary.put(type, categorySummary.getOrDefault(type, 0) + 1);
        }

        HashMap<String, Object> summary = new HashMap<>();
        summary.put("Total Tasks", totalTasks);
        summary.put("Completed", completed);
        summary.put("Incomplete", incomplete);
        summary.put("Completion Rate", completionRate);
        summary.put("Task Categories", categorySummary);

        return summary;
    }
}


