package com.mitaoe.shridhar202401040197.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {

    @Insert
    long insertUser(User user);

    @Query("SELECT * FROM user_table WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    User findByEmail(String email);

    @Query("SELECT * FROM user_table WHERE userId = :userId LIMIT 1")
    User findById(long userId);
}
