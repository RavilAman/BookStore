package kz.halykacademy.bookstore.service.orders;

import kz.halykacademy.bookstore.dao.books.BookEntity;
import kz.halykacademy.bookstore.dao.books.BookRepository;
import kz.halykacademy.bookstore.dao.orderBook.OrderBook;
import kz.halykacademy.bookstore.dao.orders.OrderEntity;
import kz.halykacademy.bookstore.dao.orders.OrderRepository;
import kz.halykacademy.bookstore.dao.users.UserEntity;
import kz.halykacademy.bookstore.service.users.UserServiceImpl;
import kz.halykacademy.bookstore.web.exceptionHandling.ResourceNotFoundException;
import kz.halykacademy.bookstore.web.exceptionHandling.UserBadRequestException;
import kz.halykacademy.bookstore.web.orders.Order;
import kz.halykacademy.bookstore.web.orders.SaveOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceImpl userService;
    private final BookRepository bookRepository;


    @Override
    public List<Order> getAll() {
        return orderRepository.findAll().stream().map(OrderEntity::toDto).collect(Collectors.toList());
    }

    @Override
    public List<Order> getAllForSpecificUser(String username) {
        return orderRepository.findOrderEntitiesByUser_Username(username).stream().map(OrderEntity::toDto).collect(Collectors.toList());
    }

    @Override
    public Order getOne(Long id) {
        return orderRepository.findById(id).map(OrderEntity::toDto).orElseThrow(() -> new ResourceNotFoundException("Order not found. Invalid id supplied!"));
    }

    @Override
    public Order postOrder(String username, SaveOrder saveOrder) {

        UserEntity user = userService.findByUsername(username);

        OrderEntity order = orderRepository.save(
                new OrderEntity(
                        null,
                        user,
                        "created",
                        LocalDateTime.now(),
                        new ArrayList<>()
                ));

        order.setOrderedBooks(getOrderBooks(order, saveOrder));

        return orderRepository.save(order).toDto();
    }

    @Transactional
    public Order changeOrderStatus(String newStatus, Long id) {
        if (!orderRepository.existsById(id))
            throw new ResourceNotFoundException("Order not found! Invalid id supplied");

        orderRepository.changeOrderStatus(newStatus, id);
        return orderRepository.getReferenceById(id).toDto();
    }

    @Override
    public void deleteOrder(Long id) {
        OrderEntity canceledOrder = orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found! Invalid id supplied"));
        canceledOrder.setOrderStatus("canceled");
        canceledOrder.setOrderTime(LocalDateTime.now());
        returnBooks(canceledOrder.getOrderedBooks());
        canceledOrder.getOrderedBooks().clear();

        orderRepository.save(canceledOrder);
    }

    public List<OrderBook> getOrderBooks(OrderEntity orderEntity, SaveOrder saveOrder) {

        if (saveOrder.getOrderedBooks().size() != saveOrder.getBookAmount().size())
            throw new UserBadRequestException("Discrepancy between the number of ordered books and their amount!");


        List<OrderBook> orderBookList = new ArrayList<>();

        List<BookEntity> orderedBooks = new ArrayList<>();

        List<Integer> bookAmount = saveOrder.getBookAmount();


        for (Long id : saveOrder.getOrderedBooks()) {
            orderedBooks.add(bookRepository.findById(id).orElseThrow(
                    () -> new ResourceNotFoundException("Book not found! Book with id: " + id + " does not exist")
            ));
        }

        int count = 0;

        for (int i = 0; i < saveOrder.getOrderedBooks().size(); i++) {
            count += orderedBooks.get(i).getPrice() * bookAmount.get(i);
            if (count > 10000) {
                orderRepository.delete(orderRepository.findTopByOrderByOrderIdDesc());
                throw new UserBadRequestException("Total price of order should be less than 10000???.");
            }
        }

        count = 0;

        for (BookEntity book : orderedBooks) {
            OrderBook orderBook = new OrderBook();
            orderBook.setOrder(orderEntity);
            orderBook.setBook(book);
            orderBook.setOrdered_book_amount(bookAmount.get(count));

            int checkBookAmount = book.getBookQuantity() - bookAmount.get(count);

            if (checkBookAmount < 0) {
                throw new UserBadRequestException("Not enough books!");
            }

            book.setBookQuantity(checkBookAmount);
            orderBookList.add(orderBook);
            count++;
        }

        return orderBookList;
    }

    public void returnBooks(List<OrderBook> orderedBooks) {
        for (OrderBook orderedBook: orderedBooks) {
            BookEntity book = orderedBook.getBook();
            book.setBookQuantity(
                    book.getBookQuantity() + orderedBook.getOrdered_book_amount()
            );
            orderedBook.setOrdered_book_amount(0);
        }
    }
}
