package com.jobtrace.interviews;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

abstract class PublicInterviewDatabaseTest extends InterviewReadDatabaseTest {

    @Autowired private DataSource dataSource;

    @BeforeEach
    void seedPublicOperations() {
        jdbc.execute("""
                drop table if exists interview_review_likes,interview_review_comments,
                  interview_review_views,users cascade
                """);
        var populator = new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/public-interview-operations.sql"));
        populator.setSeparator("@@");
        populator.execute(dataSource);
        jdbc.update("""
                insert into users(id,username,display_username,display_name,image) values
                ('owner-a','alice','Alice','Alice Account',null),
                ('owner-b','bob','Bob','Bob Account','https://example.test/bob.png')
                """);
        jdbc.update("update applications set city='Shanghai' where id=?::uuid", APPLICATION_ID);
        jdbc.update("update applications set city='Beijing' where id=?::uuid", OTHER_APPLICATION_ID);
        jdbc.update("""
                update interview_reviews set highlights='Helpful tips',gaps='Practice more'
                where id=?::uuid
                """, R102);
        jdbc.update("""
                update interview_questions set question='Question\n\n**自评分：5**\n\n## 下一步行动\nSecret task',
                  original_answer='Original answer',follow_up_notes='Follow up',
                  improved_answer='Improved answer',self_rating=5
                where interview_review_id=?::uuid
                """, R102);
        jdbc.update("""
                update interview_reviews set author_mode='attributed',like_count=3,
                  published_at='2026-09-22T08:00:00Z'
                where id=?::uuid
                """, R201);
        jdbc.update("""
                insert into interview_review_comments(id,interview_review_id,user_id,content,created_at)
                values('00000000-0000-0000-0000-000000000701',?::uuid,'owner-b',
                  'Useful!', '2026-09-23T08:00:00Z')
                """, R102);
        jdbc.update("update interview_reviews set comment_count=1 where id=?::uuid", R102);
    }
}
