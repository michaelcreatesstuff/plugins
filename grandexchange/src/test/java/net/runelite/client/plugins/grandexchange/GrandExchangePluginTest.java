/*
 * Copyright (c) 2020, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.grandexchange;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemID;
import net.runelite.api.WorldType;
import net.runelite.client.Notifier;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.OpenOSRSConfig;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import static net.runelite.client.plugins.grandexchange.GrandExchangePlugin.findFuzzyIndices;
import static net.runelite.http.api.RuneLiteAPI.GSON;
import net.runelite.http.api.ge.GrandExchangeClient;
import net.runelite.http.api.ge.GrandExchangeTrade;
import net.runelite.http.api.osbuddy.OSBGrandExchangeClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GrandExchangePluginTest
{
	@Inject
	private GrandExchangePlugin grandExchangePlugin;

	@Mock
	@Bind
	private GrandExchangeConfig grandExchangeConfig;

	@Mock
	@Bind
	private Notifier notifier;

	@Mock
	@Bind
	private SessionManager sessionManager;

	@Mock
	@Bind
	private ConfigManager configManager;

	@Mock
	@Bind
	private ItemManager itemManager;

	@Mock
	@Bind
	private KeyManager keyManager;

	@Mock
	@Bind
	private MouseManager mouseManager;

	@Mock
	@Bind
	private ScheduledExecutorService scheduledExecutorService;

	@Mock
	@Bind
	private GrandExchangeClient grandExchangeClient;

	@Mock
	@Bind
	private OSBGrandExchangeClient osbGrandExchangeClient;

	@Mock
	@Bind
	private Client client;

	@Mock
	@Bind
	private RuneLiteConfig runeLiteConfig;

	@Mock
	@Bind
	private OpenOSRSConfig openOSRSConfig;

	@Before
	public void setUp()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		when(client.getUsername()).thenReturn("adam");
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
	}

	@Test
	public void testFindFuzzyIndices()
	{
		List<Integer> fuzzyIndices = findFuzzyIndices("Ancestral robe bottom", "obby");
		// r<u>ob</u>e <u>b</u>ottom
		assertEquals(Arrays.asList(11, 12, 15), fuzzyIndices);
	}

	@Test
	public void testSubmitTrade()
	{
		// 1 @ 25
		SavedOffer savedOffer = new SavedOffer();
		savedOffer.setItemId(ItemID.ABYSSAL_WHIP);
		savedOffer.setQuantitySold(1);
		savedOffer.setTotalQuantity(10);
		savedOffer.setPrice(1000);
		savedOffer.setSpent(25);
		savedOffer.setState(GrandExchangeOfferState.BUYING);
		when(configManager.getConfiguration("geoffer.adam", "0")).thenReturn(GSON.toJson(savedOffer));

		// buy 2 @ 10/ea
		GrandExchangeOffer grandExchangeOffer = mock(GrandExchangeOffer.class);
		when(grandExchangeOffer.getQuantitySold()).thenReturn(1 + 2);
		when(grandExchangeOffer.getItemId()).thenReturn(ItemID.ABYSSAL_WHIP);
		when(grandExchangeOffer.getTotalQuantity()).thenReturn(10);
		when(grandExchangeOffer.getPrice()).thenReturn(1000);
		when(grandExchangeOffer.getSpent()).thenReturn(25 + 10 * 2);
		when(grandExchangeOffer.getState()).thenReturn(GrandExchangeOfferState.BUYING);
		grandExchangePlugin.submitTrade(0, grandExchangeOffer);

		ArgumentCaptor<GrandExchangeTrade> captor = ArgumentCaptor.forClass(GrandExchangeTrade.class);
		verify(grandExchangeClient).submit(captor.capture());

		GrandExchangeTrade trade = captor.getValue();
		assertTrue(trade.isBuy());
		assertEquals(ItemID.ABYSSAL_WHIP, trade.getItemId());
		assertEquals(2, trade.getDqty());
		assertEquals(10, trade.getTotal());
		assertEquals(45, trade.getSpent());
		assertEquals(20, trade.getDspent());
	}

	@Test
	public void testDuplicateTrade()
	{
		SavedOffer savedOffer = new SavedOffer();
		savedOffer.setItemId(ItemID.ABYSSAL_WHIP);
		savedOffer.setQuantitySold(1);
		savedOffer.setTotalQuantity(10);
		savedOffer.setPrice(1000);
		savedOffer.setSpent(25);
		savedOffer.setState(GrandExchangeOfferState.BUYING);
		when(configManager.getConfiguration("geoffer.adam", "0")).thenReturn(GSON.toJson(savedOffer));

		GrandExchangeOffer grandExchangeOffer = mock(GrandExchangeOffer.class);
		when(grandExchangeOffer.getQuantitySold()).thenReturn(1);
		when(grandExchangeOffer.getItemId()).thenReturn(ItemID.ABYSSAL_WHIP);
		when(grandExchangeOffer.getTotalQuantity()).thenReturn(10);
		when(grandExchangeOffer.getPrice()).thenReturn(1000);
		lenient().when(grandExchangeOffer.getSpent()).thenReturn(25);
		when(grandExchangeOffer.getState()).thenReturn(GrandExchangeOfferState.BUYING);
		grandExchangePlugin.submitTrade(0, grandExchangeOffer);

		verify(grandExchangeClient, never()).submit(any(GrandExchangeTrade.class));
	}

	@Test
	public void testCancelTrade()
	{
		SavedOffer savedOffer = new SavedOffer();
		savedOffer.setItemId(ItemID.ABYSSAL_WHIP);
		savedOffer.setQuantitySold(1);
		savedOffer.setTotalQuantity(10);
		savedOffer.setPrice(1000);
		savedOffer.setSpent(25);
		savedOffer.setState(GrandExchangeOfferState.BUYING);
		when(configManager.getConfiguration("geoffer.adam", "0")).thenReturn(GSON.toJson(savedOffer));

		GrandExchangeOffer grandExchangeOffer = mock(GrandExchangeOffer.class);
		when(grandExchangeOffer.getQuantitySold()).thenReturn(1);
		when(grandExchangeOffer.getItemId()).thenReturn(ItemID.ABYSSAL_WHIP);
		when(grandExchangeOffer.getTotalQuantity()).thenReturn(10);
		when(grandExchangeOffer.getPrice()).thenReturn(1000);
		when(grandExchangeOffer.getSpent()).thenReturn(25);
		when(grandExchangeOffer.getState()).thenReturn(GrandExchangeOfferState.CANCELLED_BUY);
		grandExchangePlugin.submitTrade(0, grandExchangeOffer);

		ArgumentCaptor<GrandExchangeTrade> captor = ArgumentCaptor.forClass(GrandExchangeTrade.class);
		verify(grandExchangeClient).submit(captor.capture());

		GrandExchangeTrade trade = captor.getValue();
		assertTrue(trade.isBuy());
		assertTrue(trade.isCancel());
		assertEquals(ItemID.ABYSSAL_WHIP, trade.getItemId());
		assertEquals(1, trade.getQty());
		assertEquals(10, trade.getTotal());
		assertEquals(25, trade.getSpent());
	}
}