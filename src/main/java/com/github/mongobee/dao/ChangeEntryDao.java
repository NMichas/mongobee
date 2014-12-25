package com.github.mongobee.dao;

import static com.github.mongobee.changeset.ChangeEntry.CHANGELOG_COLLECTION;

import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.exception.MongobeeConfigurationException;
import com.github.mongobee.exception.MongobeeConnectionException;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteResult;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeEntryDao {
	private static final Logger logger = Logger.getLogger(ChangeEntryDao.class.getName());

	private DB db;
	private ChangeEntryIndexDao indexDao = new ChangeEntryIndexDao();

	public DB getDb() {
		return db;
	}

	public DB connectMongoDb(Mongo mongo, String dbName)
			throws MongobeeConfigurationException {
		if (StringUtils.isEmpty(dbName)) {
			throw new MongobeeConfigurationException(
					"DB name is not set. Should be defined in MongoDB URI or via setter");
		} else {
			db = mongo.getDB(dbName);
			ensureChangeLogCollectionIndex(db
					.getCollection(CHANGELOG_COLLECTION));
			return db;
		}
	}

	public DB connectMongoDb(MongoClientURI mongoClientURI, String dbName)
			throws MongobeeConfigurationException, MongobeeConnectionException {
		try {
			final Mongo mongoClient = new MongoClient(mongoClientURI);
			final String database = StringUtils.isEmpty(dbName) ? mongoClientURI.getDatabase() : dbName;
			return this.connectMongoDb(mongoClient, database);
		} catch (UnknownHostException e) {
			throw new MongobeeConnectionException(e.getMessage(), e);
		}

	}

	public boolean isNewChange(ChangeEntry changeEntry)
			throws MongobeeConnectionException {
		verifyDbConnection();

		DBCollection mongobeeChangeLog = getDb().getCollection(
				CHANGELOG_COLLECTION);
		DBObject entry = mongobeeChangeLog.findOne(changeEntry
				.buildSearchQueryDBObject());

		return entry == null ? true : false;
	}

	public WriteResult save(ChangeEntry changeEntry)
			throws MongobeeConnectionException {
		verifyDbConnection();

		DBCollection mongobeeLog = getDb().getCollection(CHANGELOG_COLLECTION);
		return mongobeeLog.save(changeEntry.buildFullDBObject());
	}

	private void verifyDbConnection() throws MongobeeConnectionException {
		if (getDb() == null) {
			throw new MongobeeConnectionException(
					"Database is not connected. Mongobee has thrown an unexpected error",
					new NullPointerException());
		}
	}

	private void ensureChangeLogCollectionIndex(DBCollection collection) {
		DBObject index = indexDao.findRequiredChangeAndAuthorIndex(db);
		if (index == null) {
			indexDao.createRequiredUniqueIndex(collection);
			logger.log(Level.FINE, "Index in collection " + CHANGELOG_COLLECTION
					+ " was created");
		} else if (!indexDao.isUnique(index)) {
			indexDao.dropIndex(collection, index);
			indexDao.createRequiredUniqueIndex(collection);
			logger.log(Level.FINE, "Index in collection " + CHANGELOG_COLLECTION
					+ " was recreated");
		}

	}

	/* Visible for testing */
	void setIndexDao(ChangeEntryIndexDao changeEntryIndexDao) {
		this.indexDao = changeEntryIndexDao;
	}

}
