CREATE TABLE greeting
(
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    message VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO greeting (message) VALUES ('hello from preinit');
