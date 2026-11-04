-- The existing reviews table has a review_id PK but some installations have no generated default.
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
   AND table_name='reviews' AND column_name='review_id' AND column_default IS NULL AND is_identity='NO') THEN
   CREATE SEQUENCE IF NOT EXISTS reviews_review_id_seq;
   PERFORM setval('reviews_review_id_seq',greatest(coalesce((SELECT max(review_id) FROM reviews),0)+1,
      (SELECT last_value FROM reviews_review_id_seq)),false);
   ALTER TABLE reviews ALTER COLUMN review_id SET DEFAULT nextval('reviews_review_id_seq');
   ALTER SEQUENCE reviews_review_id_seq OWNED BY reviews.review_id;
 END IF;
END $$;
