package jpatest.model;

import com.mysema.query.jpa.impl.JPAQuery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@TransactionConfiguration(transactionManager = "transactionManager", defaultRollback = true)
@ContextConfiguration(locations = {"/applicationContext-test.xml"})
@Transactional
public class QueryDSLCascadeErrorsTest
{
    @PersistenceContext
    private EntityManager entityManager;

    private Origin origin;
    private Content content;

    private QOrigin qOrigin = QOrigin.origin;
    private QContent qContent = QContent.content;

    @Before
    public void init()
    {
        origin = new Origin();
        origin.setName("Origin 1");

        content = new Content();
        content.setName("Content 1");
        content.setOrigin(origin);

        origin.getContent().add(content);

        entityManager.persist(origin);

        Assert.assertNotNull(entityManager.find(Origin.class, origin.getId()));
        Assert.assertNotNull(entityManager.find(Content.class, content.getId()));
    }

    @Test
    public void childNodesShouldBeDeleteWhenParentIsDeleted()
    {
        entityManager.remove(origin);

        Assert.assertFalse(entityManager.contains(origin));
        Assert.assertFalse(entityManager.contains(content));

        Assert.assertNull(entityManager.find(Origin.class, origin.getId()));
        Assert.assertNull(entityManager.find(Content.class, content.getId()));
    }

    @Test
    public void childNodesShouldBeDeleteWhenParentIsDeletedLoadOnlyParentNode()
    {
        Long originId = origin.getId();
        Long contentId = content.getId();

        entityManager.clear();

        // No hay objetos en el EM
        Assert.assertFalse(entityManager.contains(origin));
        Assert.assertFalse(entityManager.contains(content));

        // Objtenemos Origin, y al no estar en el EM va a la BBDD
        Origin origin2 = entityManager.find(Origin.class, originId);

        // Ahora el EM si contiene a Origin
        Assert.assertTrue(entityManager.contains(origin2));
        Assert.assertEquals(1, origin2.getContent().size());

        entityManager.remove(origin2);

        // Via EM accedemos a la BBDD para obtener el hijo Content
        Content content2 = entityManager.find(Content.class, contentId);
        // Es nulo ya que el Padre (origin) ha realizado el borrado en Cascada
        // aunque Content no estuviese en el EM
        Assert.assertNull(content2);

        // Verificamos que no está en BBDD pero si pasar por el EM
        List resultList = entityManager.createQuery("SELECT id FROM Content WHERE id=:id")
                .setParameter("id", contentId).getResultList();

        // De esta forma comprobamos que Content realmente no está en BBDD
        Assert.assertEquals(0, resultList.size());
    }

    @Test
    public void childNodesShouldBeDeleteWhenParentIsDeletedUsingQueryDSLToGetData()
    {
        Long originId = origin.getId();
        Long contentId = content.getId();

        entityManager.clear();

        // No hay objetos en el EM
        Assert.assertFalse(entityManager.contains(origin));
        Assert.assertFalse(entityManager.contains(content));

        JPAQuery query = new JPAQuery(entityManager);

        List<Origin> listaOrigins = query.from(qOrigin).where(qOrigin.id.eq(originId)).list(qOrigin);
        Origin originViaQueryDSL = listaOrigins.get(0);

        // Ahora el EM si contiene a Origin
        Assert.assertTrue(entityManager.contains(originViaQueryDSL));
        Assert.assertEquals(1, originViaQueryDSL.getContent().size());

        entityManager.remove(originViaQueryDSL);

        // Via EM accedemos a la BBDD para obtener el hijo Content
        Content content2 = entityManager.find(Content.class, contentId);
        // Es nulo ya que el Padre (origin) ha realizado el borrado en Cascada
        // aunque Content no estuviese en el EM
        Assert.assertNull(content2);

        // Verificamos que no está en BBDD pero si pasar por el EM
        List resultList = entityManager.createQuery("SELECT id FROM Content WHERE id=:id")
                .setParameter("id", contentId).getResultList();

        // De esta forma comprobamos que Content realmente no está en BBDD
        Assert.assertEquals(0, resultList.size());
    }

    @Test
    public void parentShouldNotBeDeletedWhenChidlIsDeleted()
    {
        entityManager.remove(content);

        origin.setContent(null);

        // Al utilizar el EM para borrar, vemos que Origin si está pero Content no
        Assert.assertTrue(entityManager.contains(origin));
        Assert.assertFalse(entityManager.contains(content));

        Assert.assertNull(entityManager.find(Content.class, content.getId()));
        Assert.assertNotNull(entityManager.find(Origin.class, origin.getId()));
    }

    @Test
    public void parentShouldNotDeletedWhenChidlIsDeletedIntentUsingJPQL()
    {

        Long contentId = content.getId();
        Long originId = origin.getId();

        entityManager.createQuery("DELETE FROM Content WHERE id=:id")
                .setParameter("id", contentId).executeUpdate();

        Assert.assertTrue(entityManager.contains(origin));
        // Content sigue existiendo en el EM ya que ha sido borrado por JQL
        Assert.assertTrue(entityManager.contains(content));

        List resultList = entityManager.createQuery("SELECT id FROM Content WHERE id=:id")
                .setParameter("id", contentId).getResultList();

        // De esta forma comprobamos que Content realmente no está en BBDD
        Assert.assertEquals(0, resultList.size());

        Assert.assertNotNull(entityManager.find(Origin.class, origin.getId()));

        // Al tener Content en el EM no los busca en la BBDD por lo que no se da cuenta que ha sido borrado
        Assert.assertNotNull(entityManager.find(Content.class, contentId));

        // Comprobamos que el Origin está en BBDD y que no ha sido borrado con el Hijo
        List resultList2 = entityManager.createQuery("SELECT id FROM Origin WHERE id=:id")
                .setParameter("id", originId).getResultList();

        // De esta forma comprobamos que Origin realmente no está en BBDD
        Assert.assertEquals(1, resultList2.size());
    }

    @Test(expected = JpaSystemException.class)
    public void errorDeletingIfNodeHasChildsAndUsingJPQL()
    {
        Long contentId = content.getId();
        Long originId = origin.getId();

        entityManager.createQuery("DELETE FROM Origin WHERE id=:id")
                .setParameter("id", originId).executeUpdate();
    }

    @Test
    public void recoverAllColumnsForeignKey() throws Exception
    {
        JPAQuery query = new JPAQuery(entityManager);
        List<Content> contents = query.from(qContent).list(qContent);

        for (Content content : contents)
        {
            System.out.println(content.getId());
            System.out.println(content.getName());
            System.out.println(content.getOrigin().getId());
        }
    }
}