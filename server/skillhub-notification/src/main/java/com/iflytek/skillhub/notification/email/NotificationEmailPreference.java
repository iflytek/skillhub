package com.iflytek.skillhub.notification.email;

import jakarta.persistence.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;

@Entity
@Table(name="notification_email_preference",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","category"}))
public class NotificationEmailPreference {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false,length=128) private String userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private NotificationCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private NotificationEmailMode mode=NotificationEmailMode.OFF;
    @Version private long version;
    protected NotificationEmailPreference() {}
    public NotificationEmailPreference(String userId,NotificationCategory category){this.userId=userId;this.category=category;}
    public NotificationCategory getCategory(){return category;}
    public NotificationEmailMode getMode(){return mode;}
    public void setMode(NotificationEmailMode mode){this.mode=java.util.Objects.requireNonNull(mode);}
}
