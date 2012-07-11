package com.euronextclone;

import com.euronextclone.ordertypes.Limit;
import com.euronextclone.ordertypes.Peg;
import hu.akarnokd.reactive4java.base.Action1;
import hu.akarnokd.reactive4java.reactive.Reactive;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;

import static org.hamcrest.core.Is.is;

public class PegOrderTest extends BaseReactiveTest
{
    private void buyOrders(MatchingUnit matchingUnit)
    {
        matchingUnit.newOrder(Order.OrderSide.Buy, "A", 200, new OrderPrice(new Limit(), 10.5D));
        matchingUnit.newOrder(Order.OrderSide.Buy, "B", 150, new OrderPrice(new Peg(), 10.5D));
        matchingUnit.newOrder(Order.OrderSide.Buy, "B", 70, new OrderPrice(new Peg(), 10.5D));
        matchingUnit.newOrder(Order.OrderSide.Buy, "B", 125, new OrderPrice(new Limit(), 10.5D));
    }

    private void sellOrders(MatchingUnit matchingUnit)
    {
        matchingUnit.newOrder(Order.OrderSide.Sell, "C", 130, new OrderPrice(new Limit(), 10.9D));
        matchingUnit.newOrder(Order.OrderSide.Sell, "C", 350, new OrderPrice(new Limit(), 10.9D));
        matchingUnit.newOrder(Order.OrderSide.Sell, "D", 275, new OrderPrice(new Limit(), 11D));
    }

    @Test
    public void newBuyAndSellOrderTest()
    {
        final MatchingUnit matchingUnit = new MatchingUnit();
        buyOrders(matchingUnit);
        sellOrders(matchingUnit);

        MatcherAssert.assertThat("Buy Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Buy), Matchers.is(4));
        MatcherAssert.assertThat("Sell Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Sell), Matchers.is(3));
    }

    @Test
    public void addLimitOrderWithoutMatchTest()
    {
        final MatchingUnit matchingUnit = new MatchingUnit();
        buyOrders(matchingUnit);
        sellOrders(matchingUnit);

        matchingUnit.newOrder(Order.OrderSide.Buy, "E", 200, new OrderPrice(new Limit(), 10.800000000000001D));

        MatcherAssert.assertThat("Buy Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Buy), Matchers.is(5));
        MatcherAssert.assertThat("Best Buy Limit", matchingUnit.getBestLimit(Order.OrderSide.Buy), Matchers.is("10,8"));
        MatcherAssert.assertThat("Best Sell Limit", matchingUnit.getBestLimit(Order.OrderSide.Sell), Matchers.is("10,9"));
    }

    @Test
    public void addBuyLimitOrderWithMatchTest() throws IOException {
        MatchingUnit matchingUnit = new MatchingUnit();
        buyOrders(matchingUnit);
        sellOrders(matchingUnit);

        matchingUnit.newOrder(Order.OrderSide.Buy, "E", 200, new OrderPrice(new Limit(), 10.800000000000001D));

        final Closeable close = matchingUnit.register(Reactive.toObserver(new Action1<Trade>() {
            @Override
            public void invoke(Trade value) {
                assert value.getPrice() == 10.9;
                assert value.getSellBroker() == "C";
                assert value.getBuyBroker() == "G";
                assert value.getQuantity() == 100;
                incTradeCount();
            }
        }));

        matchingUnit.newOrder(Order.OrderSide.Buy, "G", 100, new OrderPrice(new Limit(), 10.9D));

        close.close();

        MatcherAssert.assertThat("Received Trade", getReceivedTradeCount(), is(1));
        MatcherAssert.assertThat("Buy Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Buy), Matchers.is(5));
        MatcherAssert.assertThat("Sell Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Sell), Matchers.is(3));
        MatcherAssert.assertThat("Best Limit", matchingUnit.getBestLimit(Order.OrderSide.Buy), Matchers.is("10,8"));
        MatcherAssert.assertThat("Best Sell Limit", matchingUnit.getBestLimit(Order.OrderSide.Sell), Matchers.is("10,9"));
    }

    @Test
    public void addSellLimitOrderWithMatchTest() throws IOException {
        final MatchingUnit matchingUnit = new MatchingUnit();
        buyOrders(matchingUnit);
        sellOrders(matchingUnit);

        matchingUnit.newOrder(Order.OrderSide.Buy, "E", 200, new OrderPrice(new Limit(), 10.800000000000001D));
        matchingUnit.newOrder(Order.OrderSide.Buy, "G", 100, new OrderPrice(new Limit(), 10.9D));

        final Closeable close = matchingUnit.register(Reactive.toObserver(new Action1<Trade>() {
            @Override
            public void invoke(Trade value) {
                int count = incTradeCount();
                switch (count) {
                    case 0:
                        assert value.getSellBroker() == "E";
                        assert value.getBuyBroker() == "G";
                        assert value.getQuantity() == 200;
                        assert value.getPrice() == 10.8;
                        break;
                    case 1:
                        assert value.getSellBroker() == "B";
                        assert value.getBuyBroker() == "G";
                        assert value.getQuantity() == 50;
                        assert value.getPrice() == 10.8;
                        break;
                }
            }
        }));

        matchingUnit.newOrder(Order.OrderSide.Sell, "G", 250, new OrderPrice(new Limit(), 10.800000000000001D));
        close.close();

        MatcherAssert.assertThat("Received Trade", getReceivedTradeCount(), is(2));
        MatcherAssert.assertThat("Buy Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Buy), Matchers.is(4));
        MatcherAssert.assertThat("Sell Order Depth", matchingUnit.orderBookDepth(Order.OrderSide.Sell), Matchers.is(3));
        MatcherAssert.assertThat("Best Limit", matchingUnit.getBestLimit(Order.OrderSide.Buy), Matchers.is("10,5"));
        MatcherAssert.assertThat("Best Sell Limit", matchingUnit.getBestLimit(Order.OrderSide.Sell), Matchers.is("10,9"));
    }
}
