package org.wildfly.httpclient.naming;

import javax.naming.NamingException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "JNDIWFHTTP")
interface HttpNamingClientMessages extends BasicLogger {

    HttpNamingClientMessages MESSAGES = Logger.getMessageLogger(HttpNamingClientMessages.class, HttpNamingClientMessages.class.getPackage().getName());

    @Message(id = 1, value = "Unexpected data in response")
    NamingException unexpectedDataInResponse();
}
