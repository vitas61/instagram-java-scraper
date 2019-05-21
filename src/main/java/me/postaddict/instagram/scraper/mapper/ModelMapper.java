package me.postaddict.instagram.scraper.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.igorsuhorukov.dom.transform.DomTransformer;
import com.github.igorsuhorukov.dom.transform.converter.NopTypeConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import me.postaddict.instagram.scraper.MediaUtil;
import me.postaddict.instagram.scraper.model.*;
import org.apache.commons.beanutils.BeanUtils;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContextFactory;
import org.eclipse.persistence.oxm.MediaType;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ModelMapper implements Mapper{

    private static final String AUTHENTICATED_FIELD = "authenticated";
    private final ThreadLocal<ObjectMapper> mapperThreadLocal = ThreadLocal.withInitial(ObjectMapper::new);
    @Getter(AccessLevel.PROTECTED)
    private final ConcurrentHashMap<String, ThreadLocal<Unmarshaller>> unmarshallerCache = new ConcurrentHashMap<>();

    @SneakyThrows
    public PageObject<Media> mapMedias(InputStream jsonStream){
        GraphQlResponse<PageObject<Media>> medias = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/medias.json");
        if(medias.getPayload()!=null && medias.getPayload().getNodes()!=null) {
            medias.getPayload().getNodes().forEach(this::updateMediaTime);
        }
        return medias.getPayload();
    }

    public Media mapMedia(InputStream jsonStream){
        GraphQlResponse<Media> graphQlResponse = mapObject(jsonStream,
                "me/postaddict/instagram/scraper/model/media-by-url.json");
        Media media = graphQlResponse.getPayload();
        if(media.getCommentPreview()!=null) {
            media.setCommentCount(media.getCommentPreview().getCount());
        } else {
            media.setCommentCount(0);
        }
        if(media.getCommentPreview()!=null && media.getCommentPreview().getNodes()!=null) {
            media.getCommentPreview().getNodes().forEach(this::updateCommentTime);
        }
        updateMediaTime(media);
        return graphQlResponse.getPayload();
    }

    public PageObject<Comment> mapComments(InputStream jsonStream){
        GraphQlResponse<PageObject<Comment>> comments = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/comments.json");
        if(comments.getPayload()!=null && comments.getPayload().getNodes()!=null) {
            comments.getPayload().getNodes().forEach(this::updateCommentTime);
        }
        return comments.getPayload();
    }

    public Location mapLocation(InputStream jsonStream){
        GraphQlResponse<Location> graphQlResponse = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/location.json");
        Location location = graphQlResponse.getPayload();
        location.setCount(location.getMediaRating().getMedia().getCount());
        location.getMediaRating().getMedia().getNodes().forEach(this::updateMediaTime);
        location.getMediaRating().getTopPosts().forEach(this::updateMediaTime);
        return location;
    }

    public Tag mapTag(InputStream jsonStream){
        GraphQlResponse<Tag> graphQlResponse = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/tag.json");
        if(graphQlResponse!=null && graphQlResponse.getPayload()!=null){
            Tag tag = graphQlResponse.getPayload();
            if(tag==null){
                return tag;
            }
            if(tag.getMediaRating()!=null && tag.getMediaRating().getMedia()!=null
                    && tag.getMediaRating().getMedia().getNodes()!=null) {
                tag.setCount(tag.getMediaRating().getMedia().getCount());
                tag.getMediaRating().getMedia().getNodes().forEach(this::updateMediaTime);
            }
            if(tag.getMediaRating()!=null && tag.getMediaRating().getTopPosts()!=null){
                tag.getMediaRating().getTopPosts().forEach(this::updateMediaTime);
            }
            return tag;
        }
        throw new NullPointerException();
    }

    public PageObject<Account> mapFollow(InputStream jsonStream){
        GraphQlResponse<PageObject<Account>> follow = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/follow.json");
        return follow.getPayload();
    }

    public PageObject<Account> mapFollowers(InputStream jsonStream) {
        GraphQlResponse<PageObject<Account>> followers = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/followers.json");
        return followers.getPayload();
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public ActionResponse<Comment> mapMediaCommentResponse(InputStream jsonStream) {
        ActionResponse<Comment> commentActionResponse = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/mediaCommentResponse.json", ActionResponse.class);
        updateCommentTime(commentActionResponse.getPayload());
        return commentActionResponse;
    }

    @Override
    public PageObject<Account> mapLikes(InputStream jsonStream) {
        GraphQlResponse<PageObject<Account>> likesResponse = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/mediaLikes.json");
        return likesResponse.getPayload();
    }

    public ActivityFeed mapActivity(InputStream jsonStream) {
        GraphQlResponse<ActivityFeed> activityFeed = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/activity.json");
        return activityFeed.getPayload();
    }

    private void updateMediaTime(Media media) {
        if(media.getTakenAtTimestamp()!=null && media.getTakenAtTimestamp() < MediaUtil.INSTAGRAM_BORN_YEAR){
            media.setTakenAtTimestamp(media.getTakenAtTimestamp() * TimeUnit.SECONDS.toMillis(1));
        }
    }

    private void updateCommentTime(Comment comment) {
        if(comment.getCreatedAt()!=null && comment.getCreatedAt() < MediaUtil.INSTAGRAM_BORN_YEAR){
            comment.setCreatedAt(comment.getCreatedAt() * TimeUnit.SECONDS.toMillis(1));
        }
    }

    @Override
    @SneakyThrows
    public String getLastMediaShortCode(InputStream jsonStream) {
        Node jsonDom = getDomModel(jsonStream);
        return XPathFactory.newInstance().newXPath().evaluate("//shortcode", jsonDom);
    }

    @Override
    @SneakyThrows
    public Account mapAccount(InputStream jsonStream) {
        GraphQlResponse<Account> graphQlResponse = mapObject(jsonStream, "me/postaddict/instagram/scraper/model/account-binding.json", GraphQlResponse.class);
        Account account = graphQlResponse.getPayload();
        Account accountCopy = (Account) BeanUtils.cloneBean(account);
        accountCopy.setMedia(null);
        if(account.getMedia()!=null && account.getMedia().getNodes()!=null) {
            account.getMedia().getNodes().forEach(media -> media.setOwner(accountCopy));
            account.getMedia().getNodes().forEach(this::updateMediaTime);
        }
        return account;
    }


    private Node getDomModel(InputStream jsonStream) throws java.io.IOException {
        Map<String,Object> jsonMap = mapperThreadLocal.get().readValue(jsonStream,
                new TypeReference<Map<String, Object>>() {});
        return new DomTransformer(new NopTypeConverter()).transform(Collections.singletonMap("root",jsonMap));
    }

    @Override
    @SneakyThrows
    public boolean isAuthenticated(InputStream jsonStream){
        Map<String,Object> jsonMap = mapperThreadLocal.get().readValue(jsonStream,
                new TypeReference<Map<String, Object>>() {});
        return jsonMap.get(AUTHENTICATED_FIELD) instanceof Boolean && (boolean) jsonMap.get(AUTHENTICATED_FIELD);
    }

    @SuppressWarnings("unchecked")
    protected  <T> T mapObject(InputStream jsonStream, String mappingFile){
        try {
            ThreadLocal<Unmarshaller> unmarshaller = getCachedUnmarshaller(mappingFile);
            unmarshaller.get().setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, true);
            return (T) unmarshaller.get().unmarshal(jsonStream);
        } catch (JAXBException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected  <T> T mapObject(InputStream jsonStream, String mappingFile, Class rootClass){
        try {
            ThreadLocal<Unmarshaller> unmarshaller = getCachedUnmarshaller(mappingFile);
            unmarshaller.get().setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
            return (T) unmarshaller.get().unmarshal(new StreamSource(jsonStream), rootClass).getValue();
        } catch (JAXBException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected ThreadLocal<Unmarshaller> getCachedUnmarshaller(String mappingFile) {
        return getUnmarshallerCache().
                        computeIfAbsent(mappingFile, mapping -> ThreadLocal.withInitial(() -> getUnmarshaller(mapping)));
    }

    @SneakyThrows
    protected Unmarshaller getUnmarshaller(String mappingFile){
        Map<String, Object> properties = new HashMap<>();
        properties.put(JAXBContextProperties.OXM_METADATA_SOURCE, mappingFile);
        properties.put(JAXBContextProperties.MEDIA_TYPE, MediaType.APPLICATION_JSON);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader()==null?
                Account.class.getClassLoader():Thread.currentThread().getContextClassLoader();

        JAXBContext jaxbContext = DynamicJAXBContextFactory.createContextFromOXM(classLoader, properties);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, MediaType.APPLICATION_JSON);
        return unmarshaller;
    }

}
