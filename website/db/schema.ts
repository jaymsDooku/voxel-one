import { sqliteTable, text, integer, primaryKey } from 'drizzle-orm/sqlite-core';
export const questions=sqliteTable('questions',{
 id:text('id').primaryKey(),version:integer('version').notNull(),title:text('title').notNull(),context:text('context').notNull(),options:text('options').notNull(),status:text('status').notNull(),createdAt:text('created_at').notNull(),updatedAt:text('updated_at').notNull()
});
export const answers=sqliteTable('question_answers',{
 questionId:text('question_id').notNull(),questionVersion:integer('question_version').notNull(),answer:text('answer').notNull(),updatedAt:text('updated_at').notNull(),userId:text('user_id').notNull()
},table=>[primaryKey({columns:[table.questionId,table.userId]})]);
