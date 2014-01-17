package org.openmrs.module.openhmis.inventory.api.impl;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.openhmis.inventory.api.*;
import org.openmrs.module.openhmis.inventory.api.model.*;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.Date;

public class StockOperationDataServiceImplTest extends BaseModuleContextSensitiveTest {
	IItemDataService itemDataService;
	IStockRoomDataService stockRoomDataService;
	IItemStockDataService itemStockDataService;
	ITestableStockOperationDataService service;

	IItemDataServiceTest itemTest;

	@Before
	public void before() throws Exception {
		executeDataSet(TestConstants.CORE_DATASET);
		executeDataSet(IItemDataServiceTest.ITEM_DATASET);
		executeDataSet(IStockRoomDataServiceTest.DATASET);

		stockRoomDataService = Context.getService(IStockRoomDataService.class);
		itemDataService = Context.getService(IItemDataService.class);
		itemStockDataService = Context.getService(IItemStockDataService.class);
		service = Context.getService(ITestableStockOperationDataService.class);

		itemTest = new IItemDataServiceTest();
	}

	//@Test
	public void dateTest() throws Exception {
		DateTime baseTime = new DateTime();
		DateTime o1Time = baseTime.plusDays(100);
		DateTime o2Time = baseTime.minusDays(100);

		int i = Seconds.secondsBetween(baseTime, o1Time).getSeconds();
		int i2= Seconds.secondsBetween(baseTime, o2Time).getSeconds();
	}

	/**
	 * @verifies use closest expiration from the source stock room
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldUseClosestExpirationFromTheSourceStockRoom() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item item0 = itemDataService.getById(0);
		Item item2 = itemDataService.getById(2);

		// Add some item stock with different qualifiers to the source room
		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(item2);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStockDetail detail2 = new ItemStockDetail();
		detail2.setItem(item2);
		detail2.setStockRoom(sourceRoom);
		detail2.setQuantity(20);
		detail2.setCalculatedBatch(false);
		detail2.setCalculatedExpiration(false);
		detail2.setBatchOperation(service.getById(1));
		Calendar calendar2 = Calendar.getInstance();
		calendar2.add(Calendar.YEAR, 1);
		detail2.setExpiration(calendar2.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, item2);
		stock2.addDetail(detail1);
		stock2.addDetail(detail2);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		final ReservedTransaction tx = operation.addReserved(item0, 3);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);
		final ReservedTransaction tx2 = operation.addReserved(item2, 10);
		tx2.setCalculatedBatch(true);
		tx2.setCalculatedExpiration(true);

		// Calculate the reservations
		service.calculateReservations(operation);

		// Ensure that no new transactions were created
		Assert.assertEquals(2, operation.getReserved().size());

		// Ensure that no expiration was set as the item is not expirable
		ReservedTransaction testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx;
			}
		});
		Assert.assertEquals(item0, testTx.getItem());
		Assert.assertNull(testTx.getExpiration());

		// Ensure that the closest expiration stock was selected
		testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx2;
			}
		});
		Assert.assertEquals(item2, testTx.getItem());
		Assert.assertEquals(calendar2.getTime(), testTx.getExpiration());
	}

	/**
	 * @verifies use oldest batch operation with the calculated expiration
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldUseOldestBatchOperationWithTheCalculatedExpiration() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item item0 = itemDataService.getById(0);

		// Add some item stock with different qualifiers to the source room
		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(item0);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));

		ItemStockDetail detail2 = new ItemStockDetail();
		detail2.setItem(item0);
		detail2.setStockRoom(sourceRoom);
		detail2.setQuantity(20);
		detail2.setCalculatedBatch(false);
		detail2.setCalculatedExpiration(false);
		detail2.setBatchOperation(service.getById(1));

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, item0);
		stock2.addDetail(detail1);
		stock2.addDetail(detail2);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(item0, 3);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that no new transactions were created
		Assert.assertEquals(1, operation.getReserved().size());

		// Ensure that the correct (oldest) batch operation was set
		tx = Iterators.get(operation.getReserved().iterator(), 0);
		Assert.assertEquals(item0, tx.getItem());
		Assert.assertEquals(1, (int)tx.getBatchOperation().getId());
	}

	/**
	 * @verifies set the expiration to null if no valid item stock can be found
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldSetTheExpirationToNullIfNoValidItemStockCanBeFound() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);

		itemDataService.save(newItem);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());

		ReservedTransaction tx = operation.addReserved(newItem, 3);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		Assert.assertEquals(1, operation.getReserved().size());
		Assert.assertNull(tx.getExpiration());
	}

	/**
	 * @verifies set the batch to null if no valid item stock can be found
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldSetTheBatchToNullIfNoValidItemStockCanBeFound() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);

		itemDataService.save(newItem);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());

		ReservedTransaction tx = operation.addReserved(newItem, 3);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		Assert.assertEquals(1, operation.getReserved().size());
		Assert.assertNull(tx.getBatchOperation());
	}

	/**
	 * @verifies throw IllegalArgumentException if operation is null
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test(expected = IllegalArgumentException.class)
	public void calculateReservations_shouldThrowIllegalArgumentExceptionIfOperationIsNull() throws Exception {
		service.calculateReservations(null);
	}

	/**
	 * @verifies use date and time for expiration calculation
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldUseDateAndTimeForExpirationCalculation() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item item2 = itemDataService.getById(2);

		// Add some item stock with different qualifiers to the source room
		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(item2);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStockDetail detail2 = new ItemStockDetail();
		detail2.setItem(item2);
		detail2.setStockRoom(sourceRoom);
		detail2.setQuantity(20);
		detail2.setCalculatedBatch(false);
		detail2.setCalculatedExpiration(false);
		detail2.setBatchOperation(service.getById(1));
		Calendar calendar2 = Calendar.getInstance();
		calendar2.add(Calendar.YEAR, 1);
		calendar2.add(Calendar.MINUTE, 20);
		detail2.setExpiration(calendar2.getTime());

		ItemStockDetail detail3 = new ItemStockDetail();
		detail3.setItem(item2);
		detail3.setStockRoom(sourceRoom);
		detail3.setQuantity(20);
		detail3.setCalculatedBatch(false);
		detail3.setCalculatedExpiration(false);
		detail3.setBatchOperation(service.getById(1));
		Calendar calendar3 = Calendar.getInstance();
		calendar3.add(Calendar.YEAR, 1);
		detail3.setExpiration(calendar3.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, item2);
		stock2.addDetail(detail1);
		stock2.addDetail(detail2);
		stock2.addDetail(detail3);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(item2, 10);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that no new transactions were created
		Assert.assertEquals(1, operation.getReserved().size());

		// Ensure that the closest expiration stock was selected
		tx = Iterators.get(operation.getReserved().iterator(), 0);
		Assert.assertEquals(item2, tx.getItem());
		Assert.assertEquals(calendar3.getTime(), tx.getExpiration());
	}

	/**
	 * @verifies create additional transactions when when multiple details are need to fulfill request
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldCreateAdditionalTransactionsWhenWhenMultipleDetailsAreNeedToFulfillRequest() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item item2 = itemDataService.getById(2);

		// Add some item stock with different qualifiers to the source room
		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(item2);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStockDetail detail2 = new ItemStockDetail();
		detail2.setItem(item2);
		detail2.setStockRoom(sourceRoom);
		detail2.setQuantity(20);
		detail2.setCalculatedBatch(false);
		detail2.setCalculatedExpiration(false);
		detail2.setBatchOperation(service.getById(1));
		Calendar calendar2 = Calendar.getInstance();
		calendar2.add(Calendar.YEAR, 1);
		detail2.setExpiration(calendar2.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, item2);
		stock2.addDetail(detail1);
		stock2.addDetail(detail2);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		final ReservedTransaction tx = operation.addReserved(item2, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that a new transaction was created
		Assert.assertEquals(2, operation.getReserved().size());

		// Ensure that the closest expiration stock was selected
		ReservedTransaction testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx;
			}
		});
		Assert.assertEquals(item2, testTx.getItem());
		Assert.assertEquals(calendar2.getTime(), testTx.getExpiration());
		Assert.assertEquals(1, (int)testTx.getBatchOperation().getId());
		Assert.assertEquals(20, (int)testTx.getQuantity());

		// And then the next closest
		testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input != tx;
			}
		});
		Assert.assertEquals(item2, testTx.getItem());
		Assert.assertEquals(calendar1.getTime(), testTx.getExpiration());
		Assert.assertEquals(2, (int)testTx.getBatchOperation().getId());
		Assert.assertEquals(5, (int)testTx.getQuantity());
	}

	/**
	 * @verifies create additional null qualifier transaction when there is not enough valid item stock to fulfill request
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldCreateAdditionalNullQualifierTransactionWhenThereIsNotEnoughValidItemStockToFulfillRequest() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		itemDataService.save(newItem);
		Context.flushSession();

		// Add some item stock with different qualifiers to the source room
		ItemStock stock = new ItemStock();
		stock.setItem(newItem);
		stock.setStockRoom(sourceRoom);
		stock.setQuantity(10);
		itemStockDataService.save(stock);
		Context.flushSession();

		ItemStockDetail detail1 = new ItemStockDetail();
		stock.addDetail(detail1);
		detail1.setItem(newItem);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		itemStockDataService.save(stock);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		final ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that a new transaction was created
		Assert.assertEquals(2, operation.getReserved().size());

		// Ensure that the existing stock detail was used

		ReservedTransaction testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return tx == input;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertEquals(calendar1.getTime(), testTx.getExpiration());
		Assert.assertEquals(2, (int)testTx.getBatchOperation().getId());
		Assert.assertEquals(10, (int)testTx.getQuantity());

		// Ensure that another reservation was created with no expiration or batch for the remaining items
		testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return tx != input;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertTrue(testTx.isCalculatedExpiration());
		Assert.assertNull(testTx.getExpiration());
		Assert.assertTrue(testTx.isCalculatedBatch());
		Assert.assertNull(testTx.getBatchOperation());
		Assert.assertEquals(15, (int)testTx.getQuantity());
	}

	/**
	 * @verifies copy source calculation settings into source calculation fields
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldCopySourceCalculationSettingsIntoSourceCalculationFields() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		itemDataService.save(newItem);
		Context.flushSession();

		// Add some item stock with different qualifiers to the source room
		ItemStock stock = new ItemStock();
		stock.setItem(newItem);
		stock.setStockRoom(sourceRoom);
		stock.setQuantity(100);
		itemStockDataService.save(stock);
		Context.flushSession();

		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(newItem);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(100);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(true);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, newItem);
		stock2.addDetail(detail1);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that no new transactions were created
		Assert.assertEquals(1, operation.getReserved().size());

		Assert.assertEquals(newItem, tx.getItem());
		Assert.assertFalse(tx.isSourceCalculatedBatch());
		Assert.assertTrue(tx.isSourceCalculatedExpiration());
	}

	/**
	 * @verifies throw APIException if calculate expiration is false and expiration is null for an expirable item
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test(expected = APIException.class)
	public void calculateReservations_shouldThrowAPIExceptionIfCalculateExpirationIsFalseAndExpirationIsNullForAnExpirableItem() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);
	}

	/**
	 * @verifies set the batch operation to the specified operation if there is no source stockroom
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldSetTheBatchOperationToTheSpecifiedOperationIfThereIsNoSourceStockroom() throws Exception {
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getReceipt());
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that no new transactions were created
		Assert.assertEquals(1, operation.getReserved().size());

		Assert.assertEquals(newItem, tx.getItem());
		Assert.assertEquals(operation, tx.getBatchOperation());
	}

	/**
	 * @verifies throw APIException if source stockroom is null and the expiration are not specified for an expirable item
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test(expected = APIException.class)
	public void calculateReservations_shouldThrowAPIExceptionIfSourceStockroomIsNullAndTheExpirationAreNotSpecifiedForAnExpirableItem() throws Exception {
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getReceipt());
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);
	}

	/**
	 * @verifies combine transactions for the same item stock and qualifiers
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldCombineTransactionsForTheSameItemStockAndQualifiers() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);

		itemDataService.save(newItem);
		Context.flushSession();

		// Add some item stock with different qualifiers to the source room
		ItemStock stock = new ItemStock();
		stock.setItem(newItem);
		stock.setStockRoom(sourceRoom);
		stock.setQuantity(100);
		itemStockDataService.save(stock);
		Context.flushSession();

		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(newItem);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(100);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(true);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, newItem);
		stock2.addDetail(detail1);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		operation.setStatus(StockOperationStatus.PENDING);
		ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);
		tx = operation.addReserved(newItem, 30);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that the two transactions were combined into one
		Assert.assertEquals(1, operation.getReserved().size());

		tx = Iterators.get(operation.getReserved().iterator(), 0);
		Assert.assertEquals(newItem, tx.getItem());
		Assert.assertEquals(55, (int)tx.getQuantity());
	}

	/**
	 * @verifies handle multiple transactions for the same item but with different qualifiers
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldHandleMultipleTransactionsForTheSameItemButWithDifferentQualifiers() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		itemDataService.save(newItem);
		Context.flushSession();

		// Add some item stock with different qualifiers to the source room
		ItemStock stock = new ItemStock();
		stock.setItem(newItem);
		stock.setStockRoom(sourceRoom);
		stock.setQuantity(10);
		itemStockDataService.save(stock);
		Context.flushSession();

		ItemStockDetail detail1 = new ItemStockDetail();
		stock.addDetail(detail1);
		detail1.setItem(newItem);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		itemStockDataService.save(stock);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		final ReservedTransaction tx = operation.addReserved(newItem, 15);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);
		final ReservedTransaction tx2  = operation.addReserved(newItem, 5, calendar1.getTime());
		tx2.setCalculatedBatch(true);
		tx2.setCalculatedExpiration(false);

		service.calculateReservations(operation);

		// Ensure that there are now 3 transactions
		Assert.assertEquals(3, operation.getReserved().size());

		ReservedTransaction testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertEquals(5, (int)testTx.getQuantity());
		Assert.assertEquals(calendar1.getTime(), testTx.getExpiration());
		Assert.assertTrue(testTx.isCalculatedExpiration());

		testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx2;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertEquals(5, (int)testTx.getQuantity());
		Assert.assertEquals(calendar1.getTime(), testTx.getExpiration());
		Assert.assertFalse(testTx.isCalculatedExpiration());

		testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input != tx && input != tx2;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertEquals(10, (int)testTx.getQuantity());
		Assert.assertNull(testTx.getExpiration());
		Assert.assertTrue(testTx.isCalculatedExpiration());
	}

	/**
	 * @verifies set the transaction source calculated flags if the source was calculated
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldSetTheTransactionSourceCalculatedFlagsIfTheSourceWasCalculated() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		itemDataService.save(newItem);
		Context.flushSession();

		// Add some item stock with different qualifiers to the source room
		ItemStock stock = new ItemStock();
		stock.setItem(newItem);
		stock.setStockRoom(sourceRoom);
		stock.setQuantity(10);
		itemStockDataService.save(stock);
		Context.flushSession();

		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(newItem);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(10);
		detail1.setCalculatedBatch(true);
		detail1.setCalculatedExpiration(true);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, newItem);
		stock2.addDetail(detail1);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		ReservedTransaction tx = operation.addReserved(newItem, 5);
		tx.setExpiration(detail1.getExpiration());
		tx.setBatchOperation(detail1.getBatchOperation());
		tx.setCalculatedBatch(false);
		tx.setCalculatedExpiration(false);

		service.calculateReservations(operation);

		Assert.assertEquals(1, operation.getReserved().size());

		Assert.assertEquals(newItem, tx.getItem());
		Assert.assertEquals(5, (int)tx.getQuantity());
		Assert.assertTrue(tx.isSourceCalculatedBatch());
		Assert.assertTrue(tx.isSourceCalculatedExpiration());
	}

	/**
	 * @verifies process non-calculated transactions before calculated transactions
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldProcessNoncalculatedTransactionsBeforeCalculatedTransactions() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		newItem.setHasExpiration(true);
		itemDataService.save(newItem);
		Context.flushSession();

		// Add some item stock with different qualifiers to the source room
		ItemStock stock = new ItemStock();
		stock.setItem(newItem);
		stock.setStockRoom(sourceRoom);
		stock.setQuantity(10);
		itemStockDataService.save(stock);
		Context.flushSession();

		ItemStockDetail detail1 = new ItemStockDetail();
		detail1.setItem(newItem);
		detail1.setStockRoom(sourceRoom);
		detail1.setQuantity(20);
		detail1.setCalculatedBatch(false);
		detail1.setCalculatedExpiration(false);
		detail1.setBatchOperation(service.getById(2));
		Calendar calendar1 = Calendar.getInstance();
		calendar1.add(Calendar.YEAR, 5);
		detail1.setExpiration(calendar1.getTime());

		ItemStockDetail detail2 = new ItemStockDetail();
		detail2.setItem(newItem);
		detail2.setStockRoom(sourceRoom);
		detail2.setQuantity(10);
		detail2.setCalculatedBatch(false);
		detail2.setCalculatedExpiration(false);
		detail2.setBatchOperation(service.getById(2));
		Calendar calendar2 = Calendar.getInstance();
		calendar2.add(Calendar.YEAR, 10);
		detail2.setExpiration(calendar2.getTime());

		ItemStock stock2 = stockRoomDataService.getItem(sourceRoom, newItem);
		stock2.addDetail(detail1);
		stock2.addDetail(detail2);

		itemStockDataService.save(stock2);
		Context.flushSession();

		// Create the stock operation
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getTransfer());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setSource(sourceRoom);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");
		operation.setOperationDate(new Date());
		final ReservedTransaction tx = operation.addReserved(newItem, 5);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		final ReservedTransaction tx2 = operation.addReserved(newItem, 10);
		tx2.setExpiration(calendar2.getTime());
		tx2.setBatchOperation(detail1.getBatchOperation());
		tx2.setCalculatedBatch(false);
		tx2.setCalculatedExpiration(false);

		service.calculateReservations(operation);

		Assert.assertEquals(2, operation.getReserved().size());

		ReservedTransaction testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertEquals(5, (int)testTx.getQuantity());
		Assert.assertEquals(calendar1.getTime(), testTx.getExpiration());
		Assert.assertTrue(testTx.isCalculatedBatch());
		Assert.assertTrue(testTx.isCalculatedExpiration());

		testTx = Iterators.find(operation.getReserved().iterator(), new Predicate<ReservedTransaction>() {
			@Override
			public boolean apply(@Nullable ReservedTransaction input) {
				return input == tx2;
			}
		});
		Assert.assertEquals(newItem, testTx.getItem());
		Assert.assertEquals(10, (int)testTx.getQuantity());
		Assert.assertEquals(calendar2.getTime(), testTx.getExpiration());
		Assert.assertFalse(testTx.isCalculatedBatch());
		Assert.assertFalse(testTx.isCalculatedExpiration());
	}

	/**
	 * @verifies set batch operation to past operations before future operations
	 * @see StockOperationDataServiceImpl#calculateReservations(org.openmrs.module.openhmis.inventory.api.model.StockOperation)
	 */
	@Test
	public void calculateReservations_shouldSetBatchOperationToPastOperationsBeforeFutureOperations() throws Exception {
		StockRoom sourceRoom = stockRoomDataService.getById(0);
		StockRoom destRoom = stockRoomDataService.getById(1);

		Item newItem = itemTest.createEntity(true);
		Context.flushSession();

		// Create a stock operation in the future
		StockOperation operation = new StockOperation();
		operation.setInstanceType(WellKnownOperationTypes.getReceipt());
		operation.setStatus(StockOperationStatus.PENDING);
		operation.setDestination(destRoom);
		operation.setOperationNumber("A123");

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.YEAR, 100);
		operation.setOperationDate(cal.getTime());

		service.save(operation);

		ReservedTransaction tx = operation.addReserved(newItem, 25);
		tx.setCalculatedBatch(true);
		tx.setCalculatedExpiration(true);

		service.calculateReservations(operation);

		// Ensure that no new transactions were created
		Assert.assertEquals(1, operation.getReserved().size());

		Assert.assertEquals(newItem, tx.getItem());
		Assert.assertEquals(operation, tx.getBatchOperation());
	}
}

