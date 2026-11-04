package com.project.w_3d_world.features.catalog.repository;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class ProductReviewRepository {
    private final JdbcTemplate jdbc;
    public List<Map<String,Object>> purchase(int buyer,int product,int item){
        return jdbc.queryForList("""
                SELECT o.physical_order_id FROM physical_order_items i
                JOIN physical_orders o USING(physical_order_id) JOIN product_variants v ON v.variant_id=i.variant_id
                WHERE i.physical_order_item_id=? AND v.product_id=? AND o.buyer_user_id=?
                AND o.order_status IN ('DELIVERED','RECEIVED')
                """,item,product,buyer);
    }
    public boolean exists(int item){return jdbc.queryForObject("SELECT count(*) FROM reviews WHERE physical_order_item_id=?",Integer.class,item)>0;}
    public int create(int buyer,int product,int order,int item,int rating,String comment){
        return jdbc.queryForObject("""
                INSERT INTO reviews(user_id,product_id,physical_order_id,physical_order_item_id,rating,comment,status)
                VALUES (?,?,?,?,?,?,'PUBLISHED') RETURNING review_id
                """,Integer.class,buyer,product,order,item,rating,comment);
    }
    public void refreshRating(int product){jdbc.update("""
            UPDATE products SET average_rating=(SELECT coalesce(round(avg(rating),2),0) FROM reviews WHERE product_id=? AND status='PUBLISHED'),
              review_count=(SELECT count(*) FROM reviews WHERE product_id=? AND status='PUBLISHED') WHERE product_id=?
            """,product,product,product);}
    public boolean published(int product,int review){return jdbc.queryForObject("SELECT count(*) FROM reviews WHERE review_id=? AND product_id=? AND status='PUBLISHED'",Integer.class,review,product)>0;}
}
