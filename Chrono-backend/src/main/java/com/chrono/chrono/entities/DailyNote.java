package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_notes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "note_date"})
})
public class DailyNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(name = "content", length = 2000)
    private String content;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getNoteDate() { return noteDate; }
    public void setNoteDate(LocalDate noteDate) { this.noteDate = noteDate; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
