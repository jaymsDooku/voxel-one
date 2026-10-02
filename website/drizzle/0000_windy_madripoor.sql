CREATE TABLE `question_answers` (
	`question_id` text NOT NULL,
	`question_version` integer NOT NULL,
	`answer` text NOT NULL,
	`updated_at` text NOT NULL,
	`user_id` text NOT NULL,
	PRIMARY KEY(`question_id`, `user_id`)
);
--> statement-breakpoint
CREATE TABLE `questions` (
	`id` text PRIMARY KEY NOT NULL,
	`version` integer NOT NULL,
	`title` text NOT NULL,
	`context` text NOT NULL,
	`options` text NOT NULL,
	`status` text NOT NULL,
	`created_at` text NOT NULL,
	`updated_at` text NOT NULL
);
